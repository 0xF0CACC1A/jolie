// Test: Array iteration with parentheses for precedence
// Expected: items[0], items[1] ((type==premium OR status==active) AND verified==true)

include "console.iol"

main {
    items[0].type = "premium";
    items[0].status = "active";
    items[0].verified = "true";

    items[1].type = "free";
    items[1].status = "active";
    items[1].verified = "true";

    items[2].type = "premium";
    items[2].status = "inactive";
    items[2].verified = "true";

    items[3].type = "premium";
    items[3].status = "active";
    items[3].verified = "false";

    select "$[*]" into results from items where "(.type == premium || .status == active) && .verified == true";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
