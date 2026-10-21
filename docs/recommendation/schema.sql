-- Aalap recommendation database (SQLite, on the phone). Source of truth: RecoDb.kt.

CREATE TABLE IF NOT EXISTS songs(
    canonical_id TEXT PRIMARY KEY,
    isrc TEXT, title TEXT NOT NULL, artist TEXT NOT NULL,
    title_key TEXT NOT NULL, artist_key TEXT NOT NULL,
    yt_id TEXT, spotify_id TEXT, saavn_id TEXT, lastfm_mbid TEXT,
    language TEXT, album TEXT, cover TEXT,
    duration_sec INTEGER NOT NULL DEFAULT 0, explicit INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0);

CREATE INDEX IF NOT EXISTS songs_isrc ON songs(isrc);

CREATE INDEX IF NOT EXISTS songs_yt ON songs(yt_id);

CREATE INDEX IF NOT EXISTS songs_spotify ON songs(spotify_id);

CREATE INDEX IF NOT EXISTS songs_saavn ON songs(saavn_id);

CREATE INDEX IF NOT EXISTS songs_title_key ON songs(title_key);

CREATE INDEX IF NOT EXISTS songs_artist_key ON songs(artist_key);

CREATE TABLE IF NOT EXISTS source_cache(
    seed_id TEXT NOT NULL, source TEXT NOT NULL,
    ranked_ids TEXT NOT NULL, fetched_at INTEGER NOT NULL,
    PRIMARY KEY(seed_id, source));

CREATE TABLE IF NOT EXISTS transitions(
    user_id TEXT NOT NULL, from_id TEXT NOT NULL, to_id TEXT NOT NULL,
    count INTEGER NOT NULL DEFAULT 0, last_at INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(user_id, from_id, to_id));

CREATE TABLE IF NOT EXISTS user_taste(
    user_id TEXT PRIMARY KEY,
    artist_weights TEXT NOT NULL DEFAULT '{}',
    liked_ids TEXT NOT NULL DEFAULT '[]',
    updated_at INTEGER NOT NULL DEFAULT 0);

CREATE TABLE IF NOT EXISTS play_history(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL, song_id TEXT NOT NULL,
    played_at INTEGER NOT NULL, skipped INTEGER NOT NULL DEFAULT 0,
    listened_ms INTEGER NOT NULL DEFAULT 0);

CREATE INDEX IF NOT EXISTS play_user_time ON play_history(user_id, played_at);
