What is this?
-
A Simple REST API Server, without any logic or proper backend

Why was this created?
-

When i was looking to teach UI development, proper APIs are hard to come by, there are few read-only APIs that give public information
but no read-write API (without any vendor lock-in like firebase)

How to Use this
-

download the Jar file and just run with JDK-17 or Later

`java -jar rest-api.jar`

What Endpoints are Available
-

The examples are given in test-api.http (intellij idea http request file)

* Create user /auth/create x-www-form-urlencoded or form-data
* Authenticate /auth/validate
* get-all of a resource GET /api/{resource-name}
* query a resource GET /api/{resource-name}?q={"key":"value"}
* get a resource GET /api/{resource-name}/{id}
* delete resource DELETE /api/{resource-name}/{id}
* update a resource PUT /api/{resource-name}/{id}

What Database is Used
-

SQLite, all resources are used with just 2 columns; id, and data of type JSON

Can i use this for Production
=
NOOO, A VERY BIG NO