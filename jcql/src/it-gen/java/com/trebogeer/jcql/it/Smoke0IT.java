/*
*   Copyright 2015 Dmitry Vasilyev
*
*   Licensed under the Apache License, Version 2.0 (the "License");
*   you may not use this file except in compliance with the License.
*   You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
*   Unless required by applicable law or agreed to in writing, software
*   distributed under the License is distributed on an "AS IS" BASIS,
*   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*   See the License for the specific language governing permissions and
*   limitations under the License.
*/

package com.trebogeer.jcql.it;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.TupleType;
import com.datastax.driver.core.TupleValue;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.UserType;
import com.trebogeer.jcql.Accounts;
import com.trebogeer.jcql.Address;
import com.trebogeer.jcql.Phone;
import org.javatuples.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://github.com/trebogeer">Dmitry Vasilyev</a>
 *         Date: 1/14/15
 *         Time: 5:15 PM
 */
public class Smoke0IT {


    static int port = Integer.getInteger("ntPort", 9042);
    static String key_space = System.getProperty("testks", "jcql");

    @Test
    public void test0() {
        try (
                Cluster c = Cluster.builder().addContactPoint("localhost").withPort(port).build();
                Session s = c.connect(key_space)
        ) {

            s.execute("INSERT INTO jcql.accounts (email, map2test) " +
                    "VALUES ('jaco@pastorius.org',{64:{alias:'bass of doom', number:'934875384'}})");
            //  Simple count, verifying ability to operate with cluster
            Row count = s.execute("SELECT COUNT(*) AS cnt FROM jcql.accounts").one();

            long cnt = count.getLong(0);

            Assert.assertEquals("Count mismatch", cnt, 1);
            Row row = s.execute("SELECT * FROM jcql.accounts WHERE email = 'jaco@pastorius.org'").one();

            Accounts a = Accounts.mapper().map(row);

            Assert.assertEquals("Wrong entry", a.getEmail(), "jaco@pastorius.org");
            Assert.assertEquals("Wrong count in map2test", a.getMap2test().size(), 1);
            s.execute("TRUNCATE jcql.accounts");
        }
    }

    @Test
    public void test1() {
        try (
                Cluster c = Cluster.builder().addContactPoint("localhost").withPort(port).build();
                Session s = c.connect(key_space)
        ) {

            PreparedStatement ps = s.prepare("INSERT INTO jcql.accounts (email, map2test, settest) " +
                    "VALUES (?,?,?)");

            BoundStatement bs = new BoundStatement(ps);

            Map<Integer, UDTValue> test = new HashMap<>();
            Phone ph = new Phone();
            ph.setAlias("fretted bass");
            ph.setNumber("6503248739");
            UserType phoneUDT = s.getCluster().getMetadata().getKeyspace(key_space).getUserType("phone");
            UDTValue uv = phoneUDT.newValue();
            uv.setString("alias", ph.getAlias());
            uv.setString("number", ph.getNumber());
            test.put(32, uv);

            HashSet<String> testset = new HashSet<>();
            testset.add("12345");
            testset.add("536455");

            Pair<Phone, String> pair = Pair.with(ph, "hi");
            TupleType tt = TupleType.of(phoneUDT, DataType.text());
            TupleValue ttv = tt.newValue();
            ttv.setUDTValue(0, uv);
            ttv.setString(1, pair.getValue1());

            bs = bs.bind("marcus@miller.com", test, testset);
            ResultSet rs = s.execute(bs);

            SimpleStatement sst = new SimpleStatement("SELECT * FROM jcql.accounts");
            sst.setFetchSize(5000);
            rs = s.execute(sst);

            List<Accounts> res = new LinkedList<>();
            Iterator<Row> iterator = rs.iterator();
            while (!rs.isExhausted() & iterator.hasNext())

            {
                Row row = iterator.next();
                res.add(Accounts.mapper().map(row));
            }
            s.execute("TRUNCATE jcql.accounts");
            Assert.assertEquals("Wrong accounts count", res.size(), 1);
        }
    }

    @Test
    public void test2() {
        try (
                Cluster c = Cluster.builder().addContactPoint("localhost").withPort(port).build();
                Session s = c.connect(key_space)
        ) {
            BatchStatement batch = new BatchStatement();

            PreparedStatement ps = s.prepare("INSERT INTO jcql.accounts (email, map2test, settest) " +
                    "VALUES (?,?,?)");

            for (int i = 0; i < 100; i++) {
                Accounts a = getTestAccount("victor@wooten@org" + i);
                BoundStatement bs = new BoundStatement(ps);
                Accounts.bind().bind(a, bs, s);
                batch.add(bs);
                if (i % 65534 == 0) {
                    ResultSet rs = s.execute(batch);
                    batch = new BatchStatement();
                }
            }

            ResultSet rs = s.execute(batch);


            rs = s.execute("SELECT * FROM jcql.accounts");
            List<Accounts> res = new LinkedList<>();
            Iterator<Row> iterator = rs.iterator();
            while (!rs.isExhausted() & iterator.hasNext())

            {
                Row row = iterator.next();
                res.add(Accounts.mapper().map(row));
            }

            s.execute("TRUNCATE jcql.accounts");
            Assert.assertEquals("Wrong accounts count", res.size(), 100);
        }

    }


    @Test
    public void test3() {
        try (
                Cluster c = Cluster.builder().addContactPoint("localhost").withPort(port).build();
                Session s = c.connect(key_space)
        ) {

            String email = "abccdef@asdf.gfd";
            Accounts account = getTestAccount(email);
            String query = "INSERT INTO accounts (addr, email, name) VALUES (?, ?, ?)";
            PreparedStatement ps1 = s.prepare(query);
            BoundStatement bs1 = new BoundStatement(ps1);

            Accounts.bind().bind(account, bs1, s);

            s.execute(bs1);
            PreparedStatement ps = s.prepare("SELECT * FROM jcql.accounts WHERE email = ?");
            Row row0 = s.execute(ps.bind(email)).one();
            Accounts a0 = Accounts.mapper().map(row0);
            s.execute("TRUNCATE jcql.accounts");
            Assert.assertEquals("To String comparison failed", a0.toString(), account.toString());
        }
    }

    @Test
    public void test4() {
        try (
                Cluster c = Cluster.builder().addContactPoint("localhost").withPort(port).build();
                Session s = c.connect(key_space)
        ) {

            String email = "abccdef@asdf.gfd";
            Accounts account = getTestAccount(email);
            String query = "INSERT INTO accounts (addr, email, name) VALUES (?, ?, ?)";
            PreparedStatement ps1 = s.prepare(query);
            BoundStatement bs1 = new BoundStatement(ps1);

            Accounts.bind().bind(account, bs1, s);

            s.execute(bs1);
            PreparedStatement ps = s.prepare("SELECT addr FROM jcql.accounts WHERE email = ?");
            Row row0 = s.execute(ps.bind(email)).one();
            Accounts a0 = Accounts.mapper().map(row0);
            s.execute("TRUNCATE jcql.accounts");
            Assert.assertEquals("To String comparison failed", a0.getAddr().toString(), account.getAddr().toString());
        }
    }



    private static Accounts getTestAccount(String email) {
        Phone p = new Phone();
        p.setAlias("home");
        p.setNumber("650-333-6666");

        Address addr = new Address();
        addr.setCity("San-Francisco");
        List<Integer> is = new LinkedList<>();
        is.add(1);
        is.add(7);
        addr.setList1test(is);
        Map<Phone, String> map = new HashMap<>();
        map.put(p, "don't call");
        addr.setMap1test(map);
        Map<Integer, String> m1 = new HashMap<>();
        m1.put(4, "hello");
        addr.setMap2test(m1);
        Map<Integer, Phone> m3 = new HashMap<>();
        m3.put(7, p);
        addr.setMap3test(m3);
        Map<Phone, Phone> m4 = new HashMap<>();
        m4.put(p, p);
        addr.setMap4test(m4);
        List<Phone> phones = new LinkedList<>();
        phones.add(p);
        addr.setPhones(phones);
        addr.setPrimaryPhone(p);
        Set<String> set = new HashSet<>();
        set.add("hi");
        addr.setSet1test(set);
        addr.setStreet("Octavia");

        Accounts account = new Accounts();
        account.setAddr(addr);
        account.setEmail(email);
        account.setName("Primary Account");
        return account;
    }

}
