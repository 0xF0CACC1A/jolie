include "console.iol"

main {
    tree.a.value = 3;
    tree.b.data.value = 12;
    tree.c.nested.value = 25;
    tree.d.value = 8;

    res << paths tree..value where $ < 5 || $ > 20;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
