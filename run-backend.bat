@echo off
REM Java项目启动脚本
REM 使用指定的JDK和Maven配置

cd /d "%~dp0backend"

set JAVA_HOME=D:\jdk
set MAVEN_HOME=D:\Apache\maven
set MAVEN_OPTS=-s D:\Apache\maven\conf\settings.xml

"%MAVEN_HOME%\bin\mvn.cmd" spring-boot:run
