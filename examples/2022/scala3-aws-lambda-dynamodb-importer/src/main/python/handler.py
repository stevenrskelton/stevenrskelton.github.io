import boto3
import json

import json

TABLE_NAME = 'demo_stock_prices'

dynamodb_table = boto3.resource('dynamodb').Table(TABLE_NAME)

def lambda_handler(event, context):
    try:
        if 'body' not in event:
            raise ParseException('empty body', "''")
        body = event['body']
        stock_price_items = parse_stock_price_items(body)
        result = put_into_dynamodb(stock_price_items)
        return {
            'statusCode': 200,
            'body': json.dumps({ "added": result })
        }
    except ParseException as ex:
        return error_to_result(ex)

class ParseException(RuntimeError):
    def __init__(self, error, content):
        self.error = error
        self.content = content

def parse_stock_price_items(str):
    print(str)
    stock_price_items = json.loads(str)
    for item in stock_price_items:
        if 'symbol' not in item:
            raise ParseException('`symbol` not found', item)
        if 'time' not in item:
            raise ParseException('`time` not found', item)
        if 'prices' not in item:
            raise ParseException('`prices` not found', item)
    return stock_price_items


def put_into_dynamodb(stock_price_items):
    try:
        with dynamodb_table.batch_writer() as batch:
            for item in stock_price_items:
                batch.put_item(Item=item)

        print('Success')
        return len(stock_price_items)
    except:
        message = f"Wrote 0 of {len(stock_price_items)}"
        print(message)
        raise

def error_to_result(ex):
    message = f"Error parsing request {ex.error} in {ex.content}"
    print(message)
    return {
        'statusCode': 400,
        'body': message
    }