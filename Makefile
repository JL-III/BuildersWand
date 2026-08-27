.PHONY: build test clean run

build:
	./gradlew build

test:
	./gradlew test

clean:
	./gradlew clean

run:
	./gradlew runServer
