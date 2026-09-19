CREATE DATABASE IF NOT EXISTS inventory_db;
CREATE DATABASE IF NOT EXISTS booking_db;

CREATE USER IF NOT EXISTS 'inventory_user'@'%' IDENTIFIED BY 'inventory_pass';
GRANT ALL PRIVILEGES ON inventory_db.* TO 'inventory_user'@'%';

CREATE USER IF NOT EXISTS 'booking_user'@'%' IDENTIFIED BY 'booking_pass';
GRANT ALL PRIVILEGES ON booking_db.* TO 'booking_user'@'%';

FLUSH PRIVILEGES;
