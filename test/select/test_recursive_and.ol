include "console.iol"

main {
    tree.a.value = 5;
    tree.b.data.value = 15;
    tree.c.nested.value = 25;
    tree.d.value = 8;

    res << select tree..value where $ > 5 && $ < 20;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
