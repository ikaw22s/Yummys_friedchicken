package yummysfriedchickeninventory;

import java.sql.Connection;

public class TestConnection {

    public static void main(String[] args) {

        Connection connection = db.getConnection();

        if (connection != null) {
            System.out.println("Connected to Yummy's database!");
        } else {
            System.out.println("Connection failed.");
        }
    }
}