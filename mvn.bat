@echo off
REM Maven便捷启动脚本
REM 使用指定的JDK和settings.xml配置文件

set JAVA_HOME=D:\jdk
set MAVEN_HOME=D:\Apache\maven
set MAVEN_OPTS=-Xmx512m
set MAVEN_SETTINGS=D:\Apache\maven\conf\settings.xml

"%MAVEN_HOME%\bin\mvn.cmd" -s "%MAVEN_SETTINGS%" %*
