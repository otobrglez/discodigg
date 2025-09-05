use clap::{Parser, Subcommand};
use discodigg::{discord, invites};
use std::error::Error;
use std::path::PathBuf;

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
    },
    ResolveFromUrls {
        #[clap(short, long, value_parser=validate_url, required = true, num_args = 1..)]
        invite_url: Vec<String>,
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

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    env_logger::init();

    match Cli::parse().command {
        Commands::ResolveFromUrls { invite_url } => {
            let urls = invites::resolve_invite_urls(invite_url)?;
            let results = invites::collect_from(urls).await?;
            print_results(results)?;
            Ok(())
        }
        Commands::ResolveFromFile { file } => {
            let invites = invites::resolve_servers_file(file)?;
            let results = invites::collect_from(invites.into_iter().map(|s| s.invite_url)).await?;
            print_results(results)?;
            Ok(())
        }
    }
}
