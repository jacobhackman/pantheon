description: Pantheon JSON-RPC API reference
<!--- END of page meta data -->

# JSON-RPC API Overview

The Pantheon JSON-RPC API uses the [JSON-RPC v2.0](https://www.jsonrpc.org/specification) specification of the JSON-RPC protocol. 
  
The [JSON](http://json.org/) (RFC 4627) format represents 
objects and data fields as collections of name/value pairs, in a readable, hierarchical form. 
Values have specific data types, like quantities (decimal integers, hexadecimal numbers, strings) and 
unformatted data (byte arrays, account addresses, hashes, and bytecode arrays).

RPC stands for Remote Procedure Call protocol (RFC 1831). RPC is stateless and transport agnostic, meaning that the concepts can be used within the same process, over sockets or HTTP, or in various message-passing environments.

The Reference documentation includes the [JSON-RPC API Methods](../Reference/JSON-RPC-API-Methods.md)
and [JSON-RPC API Objects](../Reference/JSON-RPC-API-Objects.md)
