APP := comtudia
BIN := bin/$(APP)

.PHONY: run build test vet fmt tidy check clean android-env android-build android-test wear-build

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
	@if [ -f android/env.sh ]; then . ./android/env.sh; fi; cd android && ./gradlew :app:assembleDebug

android-test:
	@if [ -f android/env.sh ]; then . ./android/env.sh; fi; cd android && ./gradlew :app:testDebugUnitTest :core:test

wear-build:
	@if [ -f android/env.sh ]; then . ./android/env.sh; fi; cd android && ./gradlew :wear:assembleDebug

clean:
	rm -rf bin
