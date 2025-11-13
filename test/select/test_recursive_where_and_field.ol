include "console.iol"

main {
    tree.a.status = "active";
    tree.a.priority = 3;
    tree.b.status = "active";
    tree.b.priority = 8;
    tree.c.status = "inactive";
    tree.c.priority = 9;

    res << select tree.* where $..status == "active" && $.priority > 5;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
