-- =====================================================================================
-- DoMoX — migration: finish the 2026-10 CRC rename on the PERSISTENCE side  (TODO-15)
-- =====================================================================================
--
-- The 2026-10 CRC-vocabulary rename changed the Java class names but deliberately PINNED
-- every persisted name, so no migration was needed at the time:
--
--     Java class                was pinned to
--     ------------------------  ----------------------------------------
--     ResponsibilityCdd         table/entity/objectType/candidateType = ActionCdd
--     AttributeCdd              ...                                  = PropertyCdd
--     CollaboratorCdd           ...                                  = AssociationCdd
--     ResponsibilityParameter   ...                                  = ParameterCdd
--
-- That left a permanent divergence: `Candidate.getObjectType()` had to read the object type
-- back from `@Named`, and every reader of the code had to re-derive that `actioncdd` is a
-- `ResponsibilityCdd`.  This script removes the divergence.
--
-- What it does, in order:
--   1. renames every constraint of the eight affected tables so its name follows the new
--      table name (Postgres keeps constraint names across ALTER TABLE ... RENAME TO; a PK
--      constraint rename also renames its index)
--   2. renames the four per-type tables and their four `_rulematch` join tables
--   3. renames the join-table discriminator columns and the `actioncdd_id` FK column of
--      `responsibilityparameter`
--   4. rewrites the persisted `candidatetype` / `relatedcandidatetype` values
--
-- Deliberately NOT touched:
--   * `domox.classcdd` / `domox.candidate` — the class name was never renamed
--   * `domox.rulematch.candidatetype` values `ClassCdd`, `GeneralizationCdd`, `SynonymCdd`
--     and the parameter kinds (`Input_Data`, `Output_Data`, `Exceptions`) — they are not
--     entity class names
--
-- Apply (back up first — `bash scripts/db-backup.sh`):
--
--     docker exec -i domox-db psql -U postgres -d postgres -v ON_ERROR_STOP=1 \
--         < scripts/migrate-crc-persisted-names.sql
--
-- The script is a one-shot: re-running it fails loudly on the first ALTER TABLE.
-- On a fresh database it is unnecessary — EclipseLink `create-tables` builds the new names
-- directly (the regenerated `domox-webapp/create-tables.sql`).
-- =====================================================================================

\set ON_ERROR_STOP on

BEGIN;

-- -------------------------------------------------------------------------------------
-- 1. constraint names follow the new table names
--    (the four tokens below are replaced in order; none of the replacement results
--     contains another token, so the mapping is unambiguous)
-- -------------------------------------------------------------------------------------
DO $$
DECLARE
    r       record;
    newname text;
    old_tables text[] := ARRAY[
        'domox.actioncdd',      'domox.propertycdd',
        'domox.associationcdd', 'domox.parametercdd',
        'domox.actioncdd_rulematch',      'domox.propertycdd_rulematch',
        'domox.associationcdd_rulematch', 'domox.parametercdd_rulematch'];
BEGIN
    FOR r IN
        SELECT c.conname, c.conrelid AS relid
        FROM pg_constraint c
        WHERE c.connamespace = 'domox'::regnamespace
          AND c.conrelid::regclass::text = ANY (old_tables)
    LOOP
        newname := r.conname;
        newname := replace(newname, 'actioncdd',      'responsibilitycdd');
        newname := replace(newname, 'propertycdd',    'attributecdd');
        newname := replace(newname, 'associationcdd', 'collaboratorcdd');
        newname := replace(newname, 'parametercdd',   'responsibilityparameter');
        IF newname <> r.conname THEN
            EXECUTE format('ALTER TABLE %s RENAME CONSTRAINT %I TO %I',
                           r.relid::regclass, r.conname, newname);
        END IF;
    END LOOP;
END $$;

-- -------------------------------------------------------------------------------------
-- 2. the per-type tables and their rule-match join tables
--    (Postgres follows FK references through a table rename)
-- -------------------------------------------------------------------------------------
ALTER TABLE domox.actioncdd      RENAME TO responsibilitycdd;
ALTER TABLE domox.propertycdd    RENAME TO attributecdd;
ALTER TABLE domox.associationcdd RENAME TO collaboratorcdd;
ALTER TABLE domox.parametercdd   RENAME TO responsibilityparameter;

ALTER TABLE domox.actioncdd_rulematch      RENAME TO responsibilitycdd_rulematch;
ALTER TABLE domox.propertycdd_rulematch    RENAME TO attributecdd_rulematch;
ALTER TABLE domox.associationcdd_rulematch RENAME TO collaboratorcdd_rulematch;
ALTER TABLE domox.parametercdd_rulematch   RENAME TO responsibilityparameter_rulematch;

-- -------------------------------------------------------------------------------------
-- 3. the columns whose names were derived from the old names
--    (the join tables' `<entityName>_ID` and `ResponsibilityParameter`'s explicit
--     @JoinColumn(name = "actioncdd_id"))
-- -------------------------------------------------------------------------------------
ALTER TABLE domox.responsibilitycdd_rulematch       RENAME COLUMN actioncdd_id      TO responsibilitycdd_id;
ALTER TABLE domox.attributecdd_rulematch            RENAME COLUMN propertycdd_id    TO attributecdd_id;
ALTER TABLE domox.collaboratorcdd_rulematch         RENAME COLUMN associationcdd_id TO collaboratorcdd_id;
ALTER TABLE domox.responsibilityparameter_rulematch RENAME COLUMN parametercdd_id   TO responsibilityparameter_id;

ALTER TABLE domox.responsibilityparameter RENAME COLUMN actioncdd_id TO responsibilitycdd_id;

-- -------------------------------------------------------------------------------------
-- 4. the persisted candidate-type values
-- -------------------------------------------------------------------------------------
UPDATE domox.rulematch SET candidatetype = 'ResponsibilityCdd'
 WHERE candidatetype = 'ActionCdd';
UPDATE domox.rulematch SET candidatetype = 'AttributeCdd'
 WHERE candidatetype = 'PropertyCdd';
UPDATE domox.rulematch SET candidatetype = 'CollaboratorCdd'
 WHERE candidatetype = 'AssociationCdd';
UPDATE domox.rulematch SET candidatetype = 'ResponsibilityParameter'
 WHERE candidatetype = 'ParameterCdd';

UPDATE domox.rulematch SET relatedcandidatetype = 'ResponsibilityCdd'
 WHERE relatedcandidatetype = 'ActionCdd';
UPDATE domox.rulematch SET relatedcandidatetype = 'AttributeCdd'
 WHERE relatedcandidatetype = 'PropertyCdd';
UPDATE domox.rulematch SET relatedcandidatetype = 'CollaboratorCdd'
 WHERE relatedcandidatetype = 'AssociationCdd';
UPDATE domox.rulematch SET relatedcandidatetype = 'ResponsibilityParameter'
 WHERE relatedcandidatetype = 'ParameterCdd';

UPDATE domox.responsibilitycdd      SET candidatetype = 'ResponsibilityCdd'       WHERE candidatetype = 'ActionCdd';
UPDATE domox.attributecdd           SET candidatetype = 'AttributeCdd'            WHERE candidatetype = 'PropertyCdd';
UPDATE domox.collaboratorcdd        SET candidatetype = 'CollaboratorCdd'         WHERE candidatetype = 'AssociationCdd';
UPDATE domox.responsibilityparameter SET candidatetype = 'ResponsibilityParameter' WHERE candidatetype = 'ParameterCdd';

-- `domox.candidate` is an empty TABLE_PER_CLASS stub with NO columns at all — nothing to update

COMMIT;

-- -------------------------------------------------------------------------------------
-- verification — every count must match the pre-migration baseline and every
-- "should_be_zero" query must return 0
-- -------------------------------------------------------------------------------------
SELECT 'classes' AS what, count(*) FROM domox.classcdd
UNION ALL SELECT 'reviews',        count(*) FROM domox.review
UNION ALL SELECT 'rulematch',      count(*) FROM domox.rulematch
UNION ALL SELECT 'responsibility', count(*) FROM domox.responsibilitycdd
UNION ALL SELECT 'attribute',      count(*) FROM domox.attributecdd
UNION ALL SELECT 'collaborator',   count(*) FROM domox.collaboratorcdd
UNION ALL SELECT 'parameter',      count(*) FROM domox.responsibilityparameter
ORDER BY 1;

SELECT 'stale candidatetype' AS check, count(*) AS should_be_zero
  FROM domox.rulematch
 WHERE candidatetype IN ('ActionCdd','PropertyCdd','AssociationCdd','ParameterCdd')
    OR relatedcandidatetype IN ('ActionCdd','PropertyCdd','AssociationCdd','ParameterCdd')
UNION ALL
SELECT 'stale per-type candidatetype', count(*)
  FROM (SELECT candidatetype FROM domox.responsibilitycdd
        UNION ALL SELECT candidatetype FROM domox.attributecdd
        UNION ALL SELECT candidatetype FROM domox.collaboratorcdd
        UNION ALL SELECT candidatetype FROM domox.responsibilityparameter) t
 WHERE candidatetype IN ('ActionCdd','PropertyCdd','AssociationCdd','ParameterCdd')
UNION ALL
SELECT 'stale table names', count(*)
  FROM information_schema.tables
 WHERE table_schema = 'domox'
   AND table_name IN ('actioncdd','propertycdd','associationcdd','parametercdd',
                      'actioncdd_rulematch','propertycdd_rulematch',
                      'associationcdd_rulematch','parametercdd_rulematch');
