rd /S /Q target 
del pom.xml
copy start-pom.xml pom.xml
IF [%1]==[] GOTO NO_BUMP
clojure -A:release %1
:NO_BUMP
clojure -A:garamond
call mvn deploy
git push --tags
