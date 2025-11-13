// Test array wildcard combined with AND: $.tags[*] == "premium" && $.status == "active"
// Should match if ANY tag is "premium" AND status is "active"

include "console.iol"

main {
    data.items[0].tags[0] = "premium";
    data.items[0].tags[1] = "featured";
    data.items[0].status = "active";

    data.items[1].tags[0] = "basic";
    data.items[1].tags[1] = "standard";
    data.items[1].status = "active";

    data.items[2].tags[0] = "premium";
    data.items[2].tags[1] = "vip";
    data.items[2].status = "inactive";

    res << paths data.items[*] where $.tags[*] == "premium" && $.status == "active";

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
