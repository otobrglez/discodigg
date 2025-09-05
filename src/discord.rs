use reqwest::Url;
use serde::{Deserialize, Serialize};
use std::error::Error;
use std::time::Duration;

#[derive(Debug, Deserialize, Clone, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct DiscordServer {
    pub name: String,
    pub invite_url: Url,
}
impl DiscordServer {}

pub struct DiscordAPIClient {
    client: reqwest::Client,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct InviteInfo {
    pub code: String,
    pub profile: ServerProfile,
    pub approximate_member_count: usize,
    pub approximate_presence_count: usize,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ServerProfile {
    pub id: String,
    pub name: String,
}

const DISCORD_API_BASE_URL: &str = "https://discord.com/api/v10";

impl DiscordAPIClient {
    pub fn new() -> Self {
        let client = reqwest::Client::builder()
            .user_agent("discodigg")
            .timeout(Duration::from_secs(2))
            .pool_max_idle_per_host(3)
            .build()
            .unwrap();
        Self { client }
    }

    pub async fn get_invite_info(&self, code: String) -> Result<InviteInfo, Box<dyn Error>> {
        let invite_url = format!(
            "{}/invites/{}?with_counts=true&with_expiration=true",
            DISCORD_API_BASE_URL, code
        );

        let response = self.client.get(invite_url.as_str()).send().await?;
        let invite_info = response.json::<InviteInfo>().await?;

        log::info!("Collected {} info.", invite_info.code);
        log::debug!("{:#?}", invite_info);

        Ok(invite_info)
    }
}
