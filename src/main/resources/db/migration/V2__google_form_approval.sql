-- Workshops now carry the Google Form that users fill in to request a seat.
-- Nullable so rows created before this migration stay valid; the API requires it for new/edited workshops.
ALTER TABLE workshops ADD COLUMN registration_form_url VARCHAR(500);

-- Registrations become approval requests: PENDING -> CONFIRMED (approved) / REJECTED.
ALTER TABLE registrations ADD COLUMN submission_source VARCHAR(20);
ALTER TABLE registrations ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE registrations ADD COLUMN reviewed_by BIGINT;
ALTER TABLE registrations ADD COLUMN review_note VARCHAR(500);
ALTER TABLE registrations ADD CONSTRAINT fk_registrations_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id);

CREATE INDEX idx_registrations_status_registered ON registrations (status, registered_at);
