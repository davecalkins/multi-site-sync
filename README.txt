MultiSiteSync was created to sync remote clients up with a
central server.  It was used as an alternative to dropbox
because a single-direction sync was desired.  The requirement
was that clients could not cause any effect to what was 
on the server; they would only pull the latest from the server.
If someone on the client machine deleted a file, it would just
re-download next update.  Whereas with dropbox a client could
delete a local file and it would be wiped out for everyone
(all other client sites).

Only the client side is provided here.  The client is a Java
application which was built into a runnable-jar for deployment.
When run it would show a notification icon and periodically
attempt to sync with the server.

The server runs in Apache and provides a URL which the client
queries to get the latest media catalog.  The client periodically
queries this and then syncs the local directory up with what
is provided by the server in the catalog.

The server catalog is maintained through a separate web application
and backend.  This allows maintaining the server side catalog
and then letting the clients all pull down the latest.

The specific use was for a central office with many satellite
locations where a media store (presentations, videos, etc.) was
maintained via the server side web app and where the clients
would then automatically pull down new content as it was available.

When files were deleted from the server catalog they would be
removed from the client dir.

Also, the server catalog provided to the client included a hash
of each file.  So even if the file did exist locally, the client
would verify the hash and if different, re-download it.  This way
if a file was somehow locally modified it would be re-downloaded
next update.