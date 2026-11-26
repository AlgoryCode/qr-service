#!/usr/bin/env bash
# Creates a full-access (role ADMIN) account for the admin QR dashboard in tbl_dashboard_user.
#
# The password is typed by you, never echoed, never passed on the command line,
# and only its BCrypt hash reaches the database.
#
# Connection: set the standard libpq env vars before running, for example
#   PGHOST=... PGPORT=5432 PGDATABASE=algoryqrdb PGUSER=postgres ./create-dashboard-admin.sh
# psql asks for the DB password itself (or reads PGPASSWORD / ~/.pgpass).
# Alternatively pass a connection string: DATABASE_URL=postgresql://user@host:5432/db ./create-dashboard-admin.sh
set -euo pipefail

command -v psql >/dev/null || { echo "psql not found" >&2; exit 1; }
command -v htpasswd >/dev/null || { echo "htpasswd not found" >&2; exit 1; }

PSQL=(psql -X -q -v ON_ERROR_STOP=1 -At)
if [[ -n "${DATABASE_URL:-}" ]]; then PSQL+=("$DATABASE_URL"); fi

q="'"
sql_literal() { printf "'%s'" "${1//$q/$q$q}"; }

# Ask for the DB password once (hidden) so every psql call below reuses it.
if [[ -z "${PGPASSWORD:-}" && -n "${PGHOST:-}" ]]; then
  read -r -s -p "DB password for ${PGUSER:-$USER}@${PGHOST}: " PGPASSWORD; echo
  export PGPASSWORD
fi

target=$("${PSQL[@]}" -c "select current_user || '@' || coalesce(inet_server_addr()::text, 'local socket') || ':' || current_setting('port') || '/' || current_database()") \
  || { echo "Could not connect to the database" >&2; exit 1; }
echo "Connected to: $target"

table_ok=$("${PSQL[@]}" -c "select to_regclass('public.tbl_dashboard_user') is not null") || exit 1
if [[ "$table_ok" != "t" ]]; then
  echo "tbl_dashboard_user does not exist on this database (migration V154 not applied?)" >&2
  exit 1
fi
has_username=$("${PSQL[@]}" -c "select exists(select 1 from information_schema.columns where table_schema='public' and table_name='tbl_dashboard_user' and column_name='username')") || exit 1

read -r -p "Email: " email
[[ "$email" == *@*.* ]] || { echo "Invalid email" >&2; exit 1; }
read -r -p "First name: " first_name
read -r -p "Last name: " last_name
[[ -n "$first_name" && -n "$last_name" ]] || { echo "First and last name are required" >&2; exit 1; }
username=""
if [[ "$has_username" == "t" ]]; then
  read -r -p "Username (optional, Enter to skip): " username
  [[ -z "$username" || "$username" != *@* ]] || { echo "Username cannot contain @" >&2; exit 1; }
fi

exists=$("${PSQL[@]}" -c "select count(*) from tbl_dashboard_user where lower(email) = lower($(sql_literal "$email"))$(
  [[ -n "$username" ]] && printf " or lower(username) = lower(%s)" "$(sql_literal "$username")")") || exit 1
[[ "$exists" == "0" ]] || { echo "An account with this email or username already exists" >&2; exit 1; }

read -r -s -p "Password (min 12 chars): " password; echo
read -r -s -p "Password again: " password2; echo
[[ "$password" == "$password2" ]] || { echo "Passwords do not match" >&2; exit 1; }
(( ${#password} >= 12 )) || { echo "Password must be at least 12 characters" >&2; exit 1; }

# BCrypt cost 10, same as Spring's BCryptPasswordEncoder default; $2y$ and $2a$ are the same algorithm.
hash=$(printf '%s' "$password" | htpasswd -niBC 10 "" | tr -d ':\n' | sed 's/^\$2y\$/$2a$/')
unset password password2
[[ "$hash" == \$2a\$10\$* && ${#hash} -eq 60 ]] || { echo "Hashing failed" >&2; exit 1; }

echo
echo "About to create on $target:"
echo "  email=$email  name=$first_name $last_name  username=${username:-<none>}  role=ADMIN  active=true"
read -r -p "Type YES to continue: " confirm
[[ "$confirm" == "YES" ]] || { echo "Cancelled"; exit 1; }

cols="first_name, last_name, email, password, role, active, created_at, updated_at"
vals="$(sql_literal "$first_name"), $(sql_literal "$last_name"), $(sql_literal "$email"), $(sql_literal "$hash"), 'ADMIN', true, now(), now()"
if [[ -n "$username" ]]; then
  cols+=", username"; vals+=", $(sql_literal "$username")"
fi

# SQL goes through stdin so the hash never shows up in the process list.
id=$("${PSQL[@]}" <<SQL
begin;
insert into tbl_dashboard_user ($cols) values ($vals) returning id;
commit;
SQL
)
id=$(printf '%s\n' "$id" | grep -E '^[0-9]+$' | head -1)
echo "Created dashboard admin id=$id ($email). Sign in at the admin dashboard with the email${username:+ or username} and your password."
