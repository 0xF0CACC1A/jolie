// Test complex nesting: objects with nested arrays at wildcard level

include "console.iol"

main {
    // Complex nested structure
    store.electronics[0].name = "Laptop";
    store.electronics[0].price = 1200;
    store.electronics[0].tags[0] = "computer";
    store.electronics[0].tags[1] = "portable";

    store.electronics[1].name = "Phone";
    store.electronics[1].price = 800;
    store.electronics[1].tags[0] = "mobile";

    store.books[0].name = "Novel";
    store.books[0].price = 25;
    store.books[0].tags[0] = "fiction";

    store.books[1].name = "Textbook";
    store.books[1].price = 95;
    store.books[1].tags[0] = "education";

    // Get all items with price > 50
    res << paths store.*[*] where $.price > 50;

    println@Console("High-price items:")();
    for (i = 0, i < #res.results, i++) {
        println@Console("  " + res.results[i])()
    }
}
