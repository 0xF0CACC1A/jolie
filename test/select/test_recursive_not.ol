include "console.iol"

main {
    tree.a.score = 5;
    tree.b.data.score = 15;
    tree.c.other = 20;

    res << select tree.* where !($..score > 10);

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
