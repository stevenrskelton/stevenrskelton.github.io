#brew install protobuf
PATH="$PATH":"$HOME/.pub-cache/bin"
mkdir -p lib/generated/protobuf;
protoc --dart_out=grpc:lib/generated/protobuf -Iprotos protos/*.proto