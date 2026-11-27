ALTER TABLE hotel ADD COLUMN breakfast_enabled INTEGER NOT NULL DEFAULT 1 CHECK (breakfast_enabled IN (0,1));
ALTER TABLE hotel ADD COLUMN lunch_enabled INTEGER NOT NULL DEFAULT 1 CHECK (lunch_enabled IN (0,1));
ALTER TABLE hotel ADD COLUMN dinner_enabled INTEGER NOT NULL DEFAULT 1 CHECK (dinner_enabled IN (0,1));
ALTER TABLE hotel ADD COLUMN payment_sound INTEGER NOT NULL DEFAULT 1 CHECK (payment_sound IN (0,1));

ALTER TABLE person ADD COLUMN identity_type TEXT NOT NULL DEFAULT 'NATIONAL_ID' CHECK (identity_type IN ('NATIONAL_ID','PASSPORT','RESIDENCE_PERMIT'));
ALTER TABLE person ADD COLUMN identity_number TEXT CHECK (identity_number IS NULL OR length(identity_number) BETWEEN 3 AND 20);
CREATE UNIQUE INDEX ux_person_identity ON person(identity_type,identity_number) WHERE identity_number IS NOT NULL;
