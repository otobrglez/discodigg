use clap::{Parser, Subcommand};
use discodigg::stats::DiscordStat;
use discodigg::{discord, invites, stats};
use std::error::Error;
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Parser)]
#[command(author, version, about, name = "discodigg", bin_name = "discodigg")]
#[command(propagate_version = true)]
#[command(args_conflicts_with_subcommands = true)]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand, Debug)]
enum Commands {
    ResolveFromFile {
        #[arg(short, long, default_value = "invites.yml")]
        file: PathBuf,
        #[arg(short, long)]
        duck_db_path: Option<PathBuf>,
        #[arg(short, long, default_value = "3")]
        concurrency_limit: Option<usize>,
        #[arg(short, long, default_value = "3")]
        max_retries: Option<usize>,
    },
    ResolveFromUrls {
        #[clap(short, long, value_parser=validate_url, required = true, num_args = 1..)]
        invite_url: Vec<String>,
        #[arg(short, long)]
        duck_db_path: Option<PathBuf>,
        #[arg(short, long, default_value = "3")]
        concurrency_limit: Option<usize>,
        #[arg(short, long, default_value = "3")]
        max_retries: Option<usize>,
    },
}

fn validate_url(raw: &str) -> Result<String, String> {
    let s = raw.trim();
    if s.starts_with("http://") || s.starts_with("https://") {
        Ok(s.to_string())
    } else {
        Err(String::from("URL must start with http:// or https://"))
    }
}

fn print_results(mut results: Vec<discord::InviteInfo>) -> Result<(), Box<dyn Error>> {
    results.sort_by(|a, b| b.approximate_member_count.cmp(&a.approximate_member_count));

    for result in results {
        println!("{}", serde_json::to_string(&result).unwrap());
    }
    Ok(())
}

fn store_results(
    results: Vec<discord::InviteInfo>,
    duck_db_path: PathBuf,
) -> Result<(), Box<dyn Error>> {
    println!("Storing results to {}", duck_db_path.display());

    let stats = stats::Measurements::open_at(duck_db_path)?;
    stats.prepare_stats_table()?;
    let collected_at_ms_utc = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64;

    for result in results {
        stats
            .append_stat(&DiscordStat {
                channel_name: result.profile.name,
                approximate_member_count: result.approximate_member_count,
                approximate_presence_count: result.approximate_presence_count,
                collected_at_ms_utc,
            })
            .unwrap()
    }

    Ok(())
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    env_logger::init();

    match Cli::parse().command {
        Commands::ResolveFromUrls {
            invite_url,
            duck_db_path: None,
            concurrency_limit,
            max_retries,
        } => {
            let urls = invites::resolve_invite_urls(invite_url)?;
            let results = invites::collect_from(urls, concurrency_limit, max_retries).await?;
            print_results(results)
        }
        Commands::ResolveFromUrls {
            invite_url,
            duck_db_path: Some(duck_db_path),
            concurrency_limit,
            max_retries,
        } => {
            let urls = invites::resolve_invite_urls(invite_url)?;
            let results = invites::collect_from(urls, concurrency_limit, max_retries).await?;
            store_results(results, duck_db_path)
        }
        Commands::ResolveFromFile {
            file,
            duck_db_path: None,
            concurrency_limit,
            max_retries,
        } => {
            let invites = invites::resolve_servers_file(file)?;
            let results = invites::collect_from(
                invites.into_iter().map(|s| s.invite_url),
                concurrency_limit,
                max_retries,
            )
            .await?;
            print_results(results)
        }
        Commands::ResolveFromFile {
            file,
            duck_db_path: Some(duck_db_path),
            concurrency_limit,
            max_retries,
        } => {
            let invites = invites::resolve_servers_file(file)?;
            let results = invites::collect_from(
                invites.into_iter().map(|s| s.invite_url),
                concurrency_limit,
                max_retries,
            )
            .await?;
            store_results(results, duck_db_path)
        }
    }
}
