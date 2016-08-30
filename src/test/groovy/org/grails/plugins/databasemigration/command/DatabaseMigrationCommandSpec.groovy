/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package org.grails.plugins.databasemigration.command

import groovy.sql.Sql
import org.h2.Driver
import org.junit.Rule
import org.springframework.boot.test.OutputCapture
import org.springframework.jdbc.datasource.DriverManagerDataSource
import spock.lang.AutoCleanup
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

abstract class DatabaseMigrationCommandSpec extends Specification {

    @Rule
    OutputCapture outputCapture = new OutputCapture()

    DataSource dataSource

    @AutoCleanup
    Connection connection

    @AutoCleanup
    Sql sql

    @AutoCleanup('deleteDir')
    File changeLogLocation

    def setup() {
        dataSource = new DriverManagerDataSource('jdbc:h2:mem:testDb', 'sa', '')
        dataSource.driverClassName = Driver.name
        connection = dataSource.connection
        sql = new Sql(connection)

        changeLogLocation = File.createTempDir()
    }


}
