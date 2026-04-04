-- V4: Rename player_id to buyer_id for naming consistency
ALTER TABLE activity_participation RENAME COLUMN player_id TO buyer_id;
ALTER TABLE activity_player_card RENAME COLUMN player_id TO buyer_id;
ALTER TABLE activity_virtual_farm RENAME COLUMN player_id TO buyer_id;
