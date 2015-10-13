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

        if(ignoreDefaultValues) {
            List<DatabaseObject> removeChanged = []
            diffResult.changedObjects.each {changedDbObject, objectDifferences ->
                if (objectDifferences.removeDifference("defaultValue")) {
                    logger.info("Ignoring default value for {}", changedDbObject.toString());
                }
                if (!objectDifferences.hasDifferences()) {
                    logger.info("removing {}, no difference left.", changedDbObject.toString());
                    removeChanged += changedDbObject
                }
            }
            removeChanged.each {
                diffResult.changedObjects.remove(it);
            }
        }


        HibernateDatabase hibernateDatabase = null
        if (referenceDatabase instanceof HibernateDatabase) {
            hibernateDatabase = (HibernateDatabase)referenceDatabase
        }
        if (hibernateDatabase) {
            String defaultSchema = targetDatabase.defaultSchemaName
            List removeMissing = []

            diffResult.missingObjects.each { dbObject ->
                if(!diffResult.getReferenceSnapshot().getDatabase().isLiquibaseObject(dbObject) &&
                   !diffResult.getReferenceSnapshot().getDatabase().isSystemObject(dbObject))
                if (dbObject instanceof liquibase.structure.core.Table) {

                    Table table = findTable(hibernateDatabase, dbObject.name)
                    if(table.schema != defaultSchema){
                        logger.warn "Removing table {} as not part of default schema {}", table.name, defaultSchema
                        removeMissing += dbObject
                        removeMissing += dbObject.outgoingForeignKeys
                        removeMissing += dbObject.primaryKey
                        removeMissing += dbObject.columns
                    }
                }
            }
            removeMissing.each {
                diffResult.missingObjects.remove(it)
            }
        }

        diffResult
    }

    static Table findTable(HibernateDatabase hibernateDatabase, String tableName){
        (Table)hibernateDatabase.configuration.tableMappings.find {Table finder ->
            finder.name == tableName}
    }
}
