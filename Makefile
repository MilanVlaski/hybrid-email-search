.PHONY: help compile clean load verify index search-semantic search-hybrid search-by-email

export MAVEN_OPTS = --add-modules jdk.incubator.vector

## Show this help message
help:
	@echo "Available commands:"
	@awk '/^## / { desc = substr($$0, 4) } /^[a-zA-Z0-9_-]+:/ && desc { sub(/:/, "", $$1); printf "  make %-16s - %s\n", $$1, desc; desc = "" }' $(MAKEFILE_LIST)

## Clean and compile the project using Maven
compile:
	mvn clean compile

## Delete the SQLite database (emails.db) and the Lucene index folder (index/)
clean:
	rm -rf emails.db index/

## Load emails from emails.csv into emails.db
load:
	mvn exec:java -Dexec.mainClass="search.usecases.LoadEmailsCsvAction" -Dexec.args="emails.csv emails.db"

## Verify the SQLite database contents
verify:
	mvn exec:java -Dexec.mainClass="search.usecases.VerifyDatabaseAction"

## Index the emails from emails.db into the Lucene index
index:
	mvn exec:java -Dexec.mainClass="search.usecases.IndexEmailsAction" \
		-Dexec.args="emails.db index"

## Run a sample semantic search query ('project discussion')
search-semantic:
	mvn exec:java -Dexec.mainClass="search.usecases.SearchEmailsAction" \
		-Dexec.args="\"project discussion\" - index"

## Run a sample hybrid search ('financial meeting' by 'john@enron.com')
search-hybrid:
	mvn exec:java -Dexec.mainClass="search.usecases.SearchEmailsAction" \
		-Dexec.args="\"financial meeting\" \"john@enron.com\" index"

## Run a sample exact match search by email ('jane@enron.com')
search-by-email:
	mvn exec:java -Dexec.mainClass="search.usecases.SearchEmailsAction" \
		-Dexec.args="- \"jane@enron.com\" index"

everything: clean compile load verify index search-semantic search-hybrid search-by-email
