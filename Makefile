APP := comtudia
BIN := bin/$(APP)

.PHONY: run build test vet fmt tidy check clean

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

clean:
	rm -rf bin
