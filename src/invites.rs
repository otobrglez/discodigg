use crate::discord;
use crate::discord::{DiscordServer, InviteInfo};
use futures::{StreamExt, TryStreamExt, stream};
use reqwest::Url;
use rust_yaml::{Value, Yaml};
use std::error::Error;
use std::path::PathBuf;
use std::time::Duration;
use tokio::time::sleep;

pub fn resolve_invite_urls(urls: Vec<String>) -> Result<Vec<Url>, Box<dyn Error>> {
    urls.into_iter()
        .map(|url| Url::parse(url.as_str()).map_err(|e| -> Box<dyn Error> { Box::new(e) }))
        .collect::<Result<Vec<Url>, _>>()
}

pub fn resolve_servers_file(path: PathBuf) -> Result<Vec<DiscordServer>, Box<dyn Error>> {
    let content = std::fs::read_to_string(path)?;
    let yaml: Value = Yaml::new().load_str(content.as_str())?;

    let servers = match yaml
        .get(&Value::String("servers".to_string()))
        .and_then(|v| v.as_sequence())
    {
        Some(s) => s,
        None => return Ok(Vec::new()),
    };

    let result = servers.iter().try_fold(Vec::new(), |mut acc, server| {
        let name = server
            .get(&Value::String("name".to_string()))
            .map(|s| s.to_string());

        let invite_url_str = server
            .get(&Value::String("invite_url".to_string()))
            .and_then(|v| v.as_str());

        if let (Some(name), Some(url_str)) = (name, invite_url_str) {
            let invite_url = Url::parse(url_str).map_err(|e| -> Box<dyn Error> { Box::new(e) })?;
            acc.push(DiscordServer { name, invite_url });
        }
        Ok::<_, Box<dyn Error>>(acc)
    })?;

    Ok(result)
}

fn get_invite_code_from_url(url: &Url) -> Result<String, Box<dyn Error>> {
    let code = url
        .path_segments()
        .and_then(|s| s.last())
        .ok_or("No code in URL")?;
    Ok(code.to_string())
}

pub async fn collect_from<I>(invite_urls: I) -> Result<Vec<InviteInfo>, Box<dyn Error>>
where
    I: IntoIterator<Item = Url>,
{
    let concurrency_limit = 2;
    let client = discord::DiscordAPIClient::new();

    let invite_codes: Vec<String> = invite_urls
        .into_iter()
        .map(|u| get_invite_code_from_url(&u))
        .collect::<Result<_, _>>()?;

    stream::iter(invite_codes)
        .map(|code| {
            let client = &client;
            async move {
                match client.get_invite_info(code.clone()).await {
                    Ok(info) => Ok::<_, Box<dyn Error>>(info),
                    Err(e) => {
                        log::warn!("Request failed. {}: {}. Retry in 5 seconds.", code, e);
                        sleep(Duration::from_secs(5)).await;
                        match client.get_invite_info(code.clone()).await {
                            Ok(info) => Ok::<_, Box<dyn Error>>(info),
                            Err(e2) => Err::<InviteInfo, _>(format!("{}: {}", code, e2).into()),
                        }
                    }
                }
            }
        })
        .buffer_unordered(concurrency_limit)
        .try_collect::<Vec<_>>()
        .await
}
