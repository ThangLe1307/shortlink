CREATE ROLE redirect_ro WITH LOGIN PASSWORD 'redirect_ro';
GRANT CONNECT ON DATABASE shortlink TO redirect_ro;
GRANT USAGE ON SCHEMA public TO redirect_ro;
