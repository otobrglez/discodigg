use duckdb::{Connection, Result, params};
use std::path::PathBuf;

pub struct DiscordStat {
    pub channel_name: String,
    pub approximate_member_count: usize,
    pub approximate_presence_count: usize,
    pub collected_at_ms_utc: i64,
}

pub struct Measurements {
    connection: Connection,
}

impl Measurements {
    pub fn open_at(path: PathBuf) -> Result<Self> {
        let connection = Connection::open(path)?;
        Ok(Self { connection })
    }

    pub fn prepare_stats_table(&self) -> Result<usize> {
        self.connection.execute(
            r#"
                CREATE TABLE IF NOT EXISTS stats (
                    channel_name TEXT,
                    approximate_member_count INTEGER,
                    approximate_presence_count INTEGER,
                    collected_at TIMESTAMPTZ
                )"#,
            params![],
        )
    }

    pub fn append_stat(&self, stat: &DiscordStat) -> Result<()> {
        self.connection.execute(
            r#"
                INSERT INTO stats (channel_name, approximate_member_count, approximate_presence_count, collected_at)
                VALUES (?, ?, ?, to_timestamp(?))"#,
            params![stat.channel_name, stat.approximate_member_count, stat.approximate_presence_count, stat.collected_at_ms_utc],
        )?;
        Ok(())
    }
}
