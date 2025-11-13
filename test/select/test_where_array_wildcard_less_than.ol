// Test array wildcard with less than: $.prices[*] < 10
// Should match if ANY price < 10

include "console.iol"

main {
    data.products[0].name = "ProductA";
    data.products[0].prices[0] = 5;
    data.products[0].prices[1] = 15;

    data.products[1].name = "ProductB";
    data.products[1].prices[0] = 20;
    data.products[1].prices[1] = 30;

    data.products[2].name = "ProductC";
    data.products[2].prices[0] = 8;
    data.products[2].prices[1] = 12;

    res << paths data.products[*] where $.prices[*] < 10;

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
