/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.databasemigration.liquibase

import liquibase.command.DiffToChangeLogCommand
import liquibase.diff.DiffResult
import liquibase.diff.output.changelog.DiffToChangeLog
import liquibase.exception.DatabaseException
import liquibase.ext.hibernate.database.HibernateDatabase
import liquibase.serializer.ChangeLogSerializerFactory
import liquibase.snapshot.InvalidExampleException
import liquibase.structure.DatabaseObject
import org.hibernate.mapping.Table
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GroovyDiffToChangeLogCommand extends DiffToChangeLogCommand {

    private static final Logger logger = LoggerFactory.getLogger(GroovyDiffToChangeLogCommand)

    private boolean ignoreDefaultValues = false

    @Override
    protected Object run() throws Exception {
        DiffResult diffResult = createDiffResult()
        try {
            if (!outputStream) {
                outputStream = System.out
            }

            if (!changeLogFile) {
                def serializer = ChangeLogSerializerFactory.instance.getSerializer('groovy')
                new DiffToChangeLog(diffResult, diffOutputControl).print(outputStream, serializer)
            }
            else {
                new DiffToChangeLog(diffResult, diffOutputControl).print(changeLogFile)
            }
        } catch (Exception ex) {
            logger.error('Exception running Diff to change log command: {}', ex)
            ex.printStackTrace()
        }
        return null
    }

    boolean getIgnoreDefaultValues() {
        return ignoreDefaultValues
    }

    GroovyDiffToChangeLogCommand setIgnoreDefaultValues(boolean ignoreDefaultValues) {
        this.ignoreDefaultValues = ignoreDefaultValues
        this
    }

    @Override
    protected DiffResult createDiffResult() throws DatabaseException, InvalidExampleException {

        DiffResult diffResult = super.createDiffResult()
        String defaultSchema = targetDatabase.defaultSchemaName
        HibernateDatabase hibernateDatabase = null
        if (referenceDatabase instanceof HibernateDatabase) {
            hibernateDatabase = (HibernateDatabase) referenceDatabase
        }
        logger.debug('setup databases')
        List<DatabaseObject> removeChanged = []
        diffResult.changedObjects.each {changedDbObject, objectDifferences ->
            if (ignoreDefaultValues) {
                if (objectDifferences.removeDifference("defaultValue")) {
                    logger.info("Ignoring default value for {}", changedDbObject.toString());
                }
                if (!objectDifferences.hasDifferences()) {
                    logger.info("removing {}, no difference left.", changedDbObject.toString());
                    removeChanged += changedDbObject
                }
            }
        }
        removeChanged.each {
            diffResult.changedObjects.remove(it);
        }
        logger.debug('Handle changed objects')

        if (hibernateDatabase) {

            List remove = []
            diffResult.missingObjects.each {it ->
                remove = verifyObjectSchema(remove, diffResult, hibernateDatabase, defaultSchema, it)
            }
            remove.each {
                diffResult.missingObjects.remove(it)
            }
            logger.debug('Handle missing objects')

            diffResult.missingObjects.findAll {it.objectTypeName == 'column' && it.getType() == null}.each {
                logger.warn('Missing column type for {}', it.name)
            }
        }


        logger.debug('created diff result: {}', diffResult)
        diffResult
    }

    static List verifyObjectSchema(List remove, DiffResult diffResult, HibernateDatabase hibernateDatabase, String defaultSchema,
                                   def dbObject) {
        if (!diffResult.getReferenceSnapshot().getDatabase().isLiquibaseObject(dbObject) &&
            !diffResult.getReferenceSnapshot().getDatabase().isSystemObject(dbObject))
            if (dbObject instanceof liquibase.structure.core.Table) {

                Collection<Table> tables = findTables(hibernateDatabase, dbObject.name)

                if (tables.size() == 1) {
                    Table table = tables[0]
                    if (table.schema != defaultSchema || table.name.startsWith('em_')) {
                        logger.warn "Removing table {} as not part of default schema {}", table.name, defaultSchema
                        remove += dbObject
                        remove += dbObject.outgoingForeignKeys
                        remove += dbObject.primaryKey
                        remove += dbObject.columns
                    }

                }
                else {
                    logger.warn('Multiple tables found for {}: {}', dbObject.name, tables)
                    dbObject.setSchema(null, defaultSchema)
                }
            }
        remove
    }

    static Collection<Table> findTables(HibernateDatabase hibernateDatabase, String tableName) {
        hibernateDatabase.configuration.tableMappings.findAll {Table finder ->
            finder.name == tableName
        }
    }
}
