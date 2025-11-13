include "console.iol"

main {
    tree.a.value = 3;
    tree.b.data.value = 7;
    tree.c.nested.value = 15;
    tree.d.deep.value = 25;
    tree.e.value = 2;

    res << select tree..value where $ > 0 && ($ < 10 || $ > 20);

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
