// Test ..field[*] with string filtering

include "console.iol"

main {
    data.users[0] = "alice";
    data.users[1] = "admin";
    data.nested.users[0] = "bob";
    data.nested.users[1] = "admin";
    data.nested.deep.users[0] = "charlie";

    // Find all 'users' arrays recursively, get only "admin"
    res << paths data..users[*] where $ == "admin";

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
