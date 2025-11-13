// Test: Composed descendant pattern with field navigation
// Expected: companies[0].departments[0].teams[0].projects[0].status, companies[0].departments[1].teams[0].projects[0].status

include "console.iol"

main {
    companies[0].departments[0].teams[0].projects[0].status = "active";
    companies[0].departments[0].teams[0].projects[0].name = "Project A";

    companies[0].departments[0].teams[0].projects[1].status = "inactive";
    companies[0].departments[0].teams[0].projects[1].name = "Project B";

    companies[0].departments[1].teams[0].projects[0].status = "active";
    companies[0].departments[1].teams[0].projects[0].name = "Project C";

    paths "$[*]..projects[*].status" into results from companies where ". == active";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
