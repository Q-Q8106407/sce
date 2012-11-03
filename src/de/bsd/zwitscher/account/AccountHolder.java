package de.bsd.zwitscher.account;

import java.util.HashSet;
import java.util.Set;

/**
 * Singleton to hold the current account
 * @author Heiko W. Rupp
 */
public class AccountHolder {
    private static AccountHolder ourInstance = new AccountHolder();

    private Account account;
    private Set<String> userNames = new HashSet<String>();
    private Set<String> hashTags = new HashSet<String>();

    public static AccountHolder getInstance() {
        return ourInstance;
    }

    private AccountHolder() {
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public Set<String> getUserNames() {
        return userNames;
    }

    public void addUserName(String name) {
        if (!name.startsWith("@")) {
            userNames.add("@"+name);
        } else {
            userNames.add(name);
        }

    }

    public Set<String> getHashTags() {
        return hashTags;
    }

    public void addHashTag(String name) {
        if (!name.startsWith("#")) {
            hashTags.add("#"+name);
        } else {
            hashTags.add(name);
        }

    }

}
