package gnome

import "time"

const (
	MAX_JOB_DURATION = 168 * time.Hour
	RECORD_INTERVAL  = 15 * time.Second
	GNOME_DB_PATH    = "gnome.db"
	GNOME_CSV_PATH   = "gnome.csv"
)
