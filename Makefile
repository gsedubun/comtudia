APP := comtudia
BIN := bin/$(APP)

.PHONY: run build test vet fmt tidy check clean android-env android-build android-test band-build

run:
	go run .

build:
	mkdir -p bin
	go build -o $(BIN) .

test:
	go test ./...

vet:
	go vet ./...

fmt:
	gofmt -w .

tidy:
	go mod tidy

check: fmt vet test

android-env:
	./tools/android-env.sh

android-build:
	cd android && ./gradlew :app:assembleDebug

android-test:
	cd android && ./gradlew :app:testDebugUnitTest

band-build:
	./tools/band-build.sh

clean:
	rm -rf bin
