.PHONY: compile load verify index search-semantic search-hybrid search-by-email

compile:
	mvn clean compile

load:
	mvn exec:java -Dexec.mainClass="search.usecases.LoadEmailsCsvAction" -Dexec.args="emails.csv emails.db"

verify:
	mvn exec:java -Dexec.mainClass="search.usecases.VerifyDatabaseAction"

index:
	mvn exec:java -Dexec.mainClass="search.usecases.IndexEmailsAction" -Dexec.args="emails.db index"

search-semantic:
	mvn exec:java -Dexec.mainClass="search.usecases.SearchEmailsAction" -Dexec.args="\"project discussion\" - index"

search-hybrid:
	mvn exec:java -Dexec.mainClass="search.usecases.SearchEmailsAction" -Dexec.args="\"financial meeting\" \"john@enron.com\" index"

search-by-email:
	mvn exec:java -Dexec.mainClass="search.usecases.SearchEmailsAction" -Dexec.args="- \"jane@enron.com\" index"
