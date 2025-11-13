// Test: Native SELECT with multiple wildcard levels (var.*.*)
// Expected output: tree.a.x, tree.a.y, tree.b.z

include "console.iol"

main {
    tree.a.x = 1;
    tree.a.y = 2;
    tree.b.z = 3;

    // Should return all grandchildren: tree.a.x, tree.a.y, tree.b.z
    res << select tree.*.* where $ > 0;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
