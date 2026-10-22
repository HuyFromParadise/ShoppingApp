@rem ----------------------------------------------------------------------------
@rem Licensed to the Apache Software Foundation (ASF) under one
@rem or more contributor license agreements.  See the NOTICE file
@rem distributed with this work for additional information
@rem regarding copyright ownership.  The ASF licenses this file
@rem to you under the Apache License, Version 2.0 (the
@rem "License"); you may not use this file except in compliance
@rem with the License.  You may obtain a copy of the License at
@rem
@rem    https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing,
@rem software distributed under the License is distributed on an
@rem "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@rem KIND, either express or implied.  See the License for the
@rem specific language governing permissions and limitations
@rem under the License.
@rem ----------------------------------------------------------------------------

@if "%DEBUG%" == "" @echo off
@if "%DEBUG%" == "on" @echo on

@setlocal EnableDelayedExpansion

set MAVEN_VERSION=3.9.6
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
for %%I in ("%DIRNAME%") do set DIRNAME=%%~fI
set MAVEN_DIST=%DIRNAME%\.mvn\wrapper\dists\apache-maven-%MAVEN_VERSION%
set MAVEN_CMD=%MAVEN_DIST%\bin\mvn.cmd
set MAVEN_ZIP_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip

if not exist "%MAVEN_CMD%" (
    echo Maven distribution not found. Downloading %MAVEN_ZIP_URL% ...
    powershell -Command "Invoke-WebRequest -Uri '%MAVEN_ZIP_URL%' -OutFile '%TEMP%\apache-maven-bin.zip'; Expand-Archive -Path '%TEMP%\apache-maven-bin.zip' -DestinationPath '%DIRNAME%\.mvn\wrapper\dists' -Force"
    if not exist "%MAVEN_CMD%" (
        echo ERROR: Maven distribution could not be installed.
        exit /b 1
    )
)

"%MAVEN_CMD%" %*
