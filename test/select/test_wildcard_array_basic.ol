// Test basic wildcard array: tree.*[*]
// Should select all array elements from all child fields

include "console.iol"

main {
    tree.a[0] = 5;
    tree.a[1] = 15;
    tree.b[0] = 25;
    tree.b[1] = 35;
    tree.c[0] = 8;

    res << paths tree.*[*] where $ > 10;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
