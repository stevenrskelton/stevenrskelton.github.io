#brew install protobuf
#dart pub global activate protoc_plugin
PATH="$PATH":"$HOME/.pub-cache/bin"
mkdir -p lib/generated/protobuf;
protoc --dart_out=grpc:lib/generated/protobuf -Iprotos protos/*.proto