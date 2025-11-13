// Test deeply nested array wildcards: $.level1[*].level2[*].level3[*]

include "console.iol"

main {
    data.orgs[0].name = "OrgA";
    data.orgs[0].depts[0].teams[0].members[0] = "Alice";
    data.orgs[0].depts[0].teams[0].members[1] = "Bob";
    data.orgs[0].depts[0].teams[1].members[0] = "Charlie";

    data.orgs[1].name = "OrgB";
    data.orgs[1].depts[0].teams[0].members[0] = "Dave";
    data.orgs[1].depts[0].teams[0].members[1] = "Alice";

    data.orgs[2].name = "OrgC";
    data.orgs[2].depts[0].teams[0].members[0] = "Eve";
    data.orgs[2].depts[0].teams[0].members[1] = "Frank";

    res << paths data.orgs[*] where $.depts[*].teams[*].members[*] == "Alice";

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
