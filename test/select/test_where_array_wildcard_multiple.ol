// Test multiple array wildcards in WHERE clause: $.groups[*].perms[*] == "admin"

include "console.iol"

main {
    data.users[0].name = "Alice";
    data.users[0].groups[0].name = "Editors";
    data.users[0].groups[0].perms[0] = "read";
    data.users[0].groups[0].perms[1] = "write";

    data.users[1].name = "Bob";
    data.users[1].groups[0].name = "Admins";
    data.users[1].groups[0].perms[0] = "read";
    data.users[1].groups[0].perms[1] = "admin";
    data.users[1].groups[1].name = "Editors";
    data.users[1].groups[1].perms[0] = "write";

    data.users[2].name = "Charlie";
    data.users[2].groups[0].name = "Viewers";
    data.users[2].groups[0].perms[0] = "read";

    res << paths data.users[*] where $.groups[*].perms[*] == "admin";

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
