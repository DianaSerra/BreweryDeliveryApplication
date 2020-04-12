/**
 * Make sure the Postgresql JDBC driver is in your classpath.
 * You can download the JDBC 4 driver from here if required.
 * https://jdbc.postgresql.org/download.html
 *
 * take care of the variables usernamestring and passwordstring to use 
 * appropriate database credentials before you compile !
 *
**/

import java.math.RoundingMode;
import java.sql.* ;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import static java.lang.Integer.parseInt;

class BreweryDeliveryApplication
{
	User user;
	ArrayList<Brewery> breweries;
	ArrayList<MenuItem> selectedMenu;
	Order currentOrder;

	BreweryDeliveryApplication(){}
	public static void main ( String [ ] args ) throws SQLException
    {
    	BreweryDeliveryApplication app= new BreweryDeliveryApplication();
		boolean loggedIn=false;


		Scanner myObj = new Scanner(System.in);  // Create a Scanner object
		try {
			DriverManager.registerDriver ( new org.postgresql.Driver() ) ;
		} catch (Exception cnfe){
			System.out.println("Class not found");
		}

		// This is the url you must use for Postgresql.
		//Note: This url may not valid now !

		String url = "jdbc:postgresql://comp421.cs.mcgill.ca:5432/cs421";
		Connection con = DriverManager.getConnection (url,"cs421g70", "beepboop70") ;
		Statement statement = con.createStatement ( ) ;

		//TODO: Implement quit for welcome screen
		while(!loggedIn){
			int loginOption= initiationScreen(myObj);

			// Register the driver.  You must register the driver before you can use it.

			if(loginOption == 1){
				loggedIn=loginUser(statement, myObj, app);
			}else{
				loggedIn=registerUser(statement, myObj, app);
			}
			System.out.println("logged in: "+loggedIn);

		}

		int mainMenuOption=1;

		//TODO: Make sure that you choosing option 0 will take you back to welcome screen
		while(mainMenuOption!=0){

			mainMenuOption=mainMenuScreen(myObj);

			if(mainMenuOption==1){
				displayAllRestaurants(statement, myObj, app);
			} else if (mainMenuOption==2){
				displayRestaurantsByRating(statement, myObj, app);
			} else if (mainMenuOption==3){
				displayRestaurantsByPriceLevel(statement, myObj, app);
			} else if (mainMenuOption==4){
				displayOrderHistory(statement, myObj,app);
			} else if (mainMenuOption==5){
				changeDeliveryAddress(statement,myObj,app);
			}
		}


		statement.close ( ) ;
		con.close ( ) ;

	}

	private static void changeDeliveryAddress(Statement statement, Scanner myObj, BreweryDeliveryApplication app) {
		System.out.println("**CHANGE DELIVERY ADDRESS**");
		System.out.println("Here is your current delivery address: ");
		try{
			String getDeliveryAddrSQL="SELECT customer.delivery_address, address.postal_code FROM customer, address WHERE customer.delivery_address=address.street_address and email= \'"+app.user.email+"\'";
			java.sql.ResultSet rs = statement.executeQuery( getDeliveryAddrSQL ) ;
			String currentStreetAddress="";
			String currentPostalCode="";
			while(rs.next()){
				currentStreetAddress=rs.getString("delivery_address");
				currentPostalCode=rs.getString("postal_code");
			}

			System.out.println(currentStreetAddress+" "+currentPostalCode);

			System.out.println("\nTo change your delivery address, enter new street address below");
			String newDeliveryAddress= myObj.nextLine();

			System.out.println("Enter new postal code below");
			String newPostalCode= myObj.nextLine();


			String setDeliveryAddrSQL= "INSERT INTO address VALUES(\'"+newDeliveryAddress+"\', \'"+newPostalCode+"\') ON CONFLICT DO NOTHING;"
					+"UPDATE customer SET delivery_address=\'"+newDeliveryAddress+"\' WHERE email=\'"+app.user.email+"\'";
			statement.executeUpdate(setDeliveryAddrSQL);

			System.out.println("Your delivery address has been changed to :\n"+newDeliveryAddress+" "+newPostalCode);

		}catch(SQLException e){
			System.out.println("Oops! Something went wrong- try again");
		}
	}

	private static void displayOrderHistory(Statement statement, Scanner myObj, BreweryDeliveryApplication app) {

		try{
			String orderHistorySQL= "SELECT cust_order.order_num, cust_order.brewery_name, cust_order.street_address, "
					+"cust_order.order_time, cust_order.tip, COALESCE(delivery.delivery_fee, 0.00) as delivery_fee"
					+" FROM cust_order LEFT OUTER JOIN delivery ON cust_order.order_num=delivery.order_num WHERE customer_email= \'"
					+app.user.email+"\';";
			java.sql.ResultSet rs = statement.executeQuery ( orderHistorySQL ) ;

			HashMap<Integer, Order> orderHashMap= new HashMap<Integer, Order>();
			String consistsOfQuery="SELECT C.order_num, I.item_name, I.cost, C.quantity\n" +
					"FROM consists_of as C, item as I\n" +
					"WHERE C.item_name=I.item_name and C.brewery_name=I.brewery_name and C.street_address=I.street_address and (";
			boolean resultNotEmpty=rs.next();
			while(resultNotEmpty){
				int oNum=rs.getInt("order_num");
				Brewery orderBrewery= new Brewery(rs.getString("brewery_name"), rs.getString("street_address"));
				Order o= new Order(orderBrewery);
				o.setOrderNum(oNum);
				o.setOrderTime(rs.getString("order_time"));
				o.setTip(rs.getDouble("tip"));
				o.setDeliveryFee(rs.getDouble("delivery_fee"));
				orderHashMap.put(oNum, o);

				consistsOfQuery+="C.order_num="+oNum;
				resultNotEmpty=rs.next();
				if(resultNotEmpty){
					consistsOfQuery+=" or ";
				}else{
					consistsOfQuery+=");";
				}
			}
			if(!orderHashMap.isEmpty()){

				java.sql.ResultSet itemsResult = statement.executeQuery ( consistsOfQuery ) ;

				while(itemsResult.next()){
					Order o= orderHashMap.get(itemsResult.getInt("order_num"));
					o.addItem(new OrderItem(itemsResult.getString("item_name"), itemsResult.getDouble("cost"),
							"","", itemsResult.getInt("quantity")));
				}

				for(Order o : orderHashMap.values()){

					System.out.println("** ORDER "+o.orderNum+" **");
					System.out.println(o.orderBrewery.name+" - "+o.orderBrewery.address);
					System.out.println("Order Date/Time: "+o.orderTime+"\n");

					o.calculateTotal();
					//add items
					printReceipt(o);
				}
			}



		}catch(SQLException e){
			System.out.println("Oops! Something went wrong, please try again!");
		}
	}
	private static void printReceipt(Order o){
		DecimalFormat df2 = new DecimalFormat("0.00");

		System.out.println("-------RECEIPT------");

		//GET ITEMS

		for(OrderItem i: o.items ) {
			System.out.println("x"+i.itemQuantity+" "+i.name + " ...... $" + df2.format(i.price));
		}
		System.out.println("\nitem total ...... $"+df2.format(o.itemTotal));
		System.out.println("taxes ........... $"+df2.format(o.taxes));
		System.out.println("delivery fee .... $"+df2.format(o.deliveryFee));
		System.out.println("tip ............. $"+df2.format(o.tip)+"\n");
		System.out.println("TOTAL ........... $"+df2.format(o.orderTotal)+"\n");
	}

	private static void displayRestaurantsByPriceLevel(Statement statement, Scanner myObj, BreweryDeliveryApplication app) {
		int sqlCode=0;      // Variable to hold SQLCODE
		String sqlState="00000";  // Variable to hold SQLSTATE
		int priceLevelSelection=0;

		while(priceLevelSelection!=1 && priceLevelSelection!=2 && priceLevelSelection!=3 && priceLevelSelection!=4) {
			System.out.println("\n**VIEW BREWERIES BY RATING **\n");
			System.out.println("Select the price point of the breweries you want to view");
			System.out.println("[1] $");
			System.out.println("[2] $$");
			System.out.println("[3] $$$");
			System.out.println("[4] $$$$");

			try {
				priceLevelSelection = myObj.nextInt();
				if(priceLevelSelection!=1 && priceLevelSelection!=2 && priceLevelSelection!=3 && priceLevelSelection!=4){
					System.out.println("Oops! You did not submit a valid input- try again!");
				}
			}catch(InputMismatchException e){
				System.out.println("Oops! You did not submit a valid input- try again!");

			}
			myObj.nextLine();


		}
		String priceLevelValue="";
		if(priceLevelSelection==1){
			priceLevelValue="low";
		}else if (priceLevelSelection==2){
			priceLevelValue="medium";
		}else if (priceLevelSelection==3){
			priceLevelValue="high";
		}else if (priceLevelSelection==4){
			priceLevelValue="very high";
		}
		//myObj.nextLine();

		try {
			String loginSQL = "SELECT * FROM brewery WHERE price_level= '"+priceLevelValue+"'";
			//System.out.println(loginSQL);
			java.sql.ResultSet rs = statement.executeQuery ( loginSQL ) ;
			ArrayList<Brewery> breweries=new ArrayList<>();
			int breweryCount= 0;
			while ( rs.next ( ) ) {
				breweryCount++;
				String breweryName = rs.getString("brewery_name");
				String address = rs.getString("street_address");
				double orderMin = rs.getDouble("order_minimum");
				String priceLevel= rs.getString("price_level");
				double breweryRating= rs.getDouble("brewery_rating");
				String openingTime= rs.getString("opening_time");
				String closingTime= rs.getString("closing_time");

				System.out.println("["+breweryCount+"] "+breweryName);
				System.out.println("	Address: "+address);
				System.out.println("	PriceLevel: "+priceLevel);
				System.out.println("	Order Minimum: "+orderMin);
				System.out.println("	Rating: "+breweryRating);
				System.out.println("	Opening hours: "+openingTime+" - "+closingTime+"\n\n");

				Brewery b= new Brewery(breweryName, address, orderMin);
				breweries.add(b);

			}
			app.breweries=breweries;


		} catch (SQLException e) {
			sqlCode = e.getErrorCode(); // Get SQLCODE
			sqlState = e.getSQLState(); // Get SQLSTATE
			System.out.println("Code: " + sqlCode + "  sqlState: " + sqlState);
		}
		selectBrewery(statement, myObj,app);

	}

	private static void selectBrewery(Statement statement, Scanner myObj, BreweryDeliveryApplication app){

		int breweryMenuItem=-1;
		while(!(breweryMenuItem<=app.breweries.size() && breweryMenuItem>=0)){
			System.out.println(" Select one of the breweries above to view their menu or enter [0] to go back to main menu");
			try{
				breweryMenuItem=myObj.nextInt();
				myObj.nextLine();
				if(breweryMenuItem==0){
					return;
				}else if (breweryMenuItem>0 && (breweryMenuItem<=app.breweries.size())){
					Brewery chosenBrewery= app.breweries.get(breweryMenuItem-1);
					displayBreweryMenu(statement, myObj, app, chosenBrewery);
				}else{
					System.out.println("Oops! You did not select a valid option- try again!");
				}
			}catch(InputMismatchException e){
				System.out.println("\nOops! You did not input an integer- try again!\n");
				myObj.nextLine();
				continue;
			}


		}
	}
	private static void displayBreweryMenu(Statement statement, Scanner myObj, BreweryDeliveryApplication app, Brewery brewery) {
		int sqlCode=0;      // Variable to hold SQLCODE
		String sqlState="00000";  // Variable to hold SQLSTATE
		DecimalFormat df2 = new DecimalFormat("0.00");
		df2.setRoundingMode(RoundingMode.UP);

		System.out.println("******** "+brewery.name+" MENU ********\n");

		try {
			String menuSQL = "SELECT item_name, cost, description, item_category FROM item WHERE brewery_name=\'"+brewery.name+
					"\' and street_address=\'"+brewery.address+"\';";
			//System.out.println(loginSQL);
			java.sql.ResultSet rs = statement.executeQuery ( menuSQL ) ;
			int itemCount= 0;
			ArrayList<MenuItem> menu= new ArrayList<>();
			while ( rs.next ( ) ) {
				itemCount++;
				String name = rs.getString("item_name");
				double price = rs.getDouble("cost");
				String description = rs.getString("description");
				String itemCategory= rs.getString("item_category");

				System.out.println("["+itemCount+"] "+name);
				System.out.println("	"+itemCategory);
				System.out.println("	Price: $"+df2.format(price));
				if(description!=null) {
					System.out.println("	Description: " + description);
				}

				MenuItem i= new MenuItem(name, price, description);
				menu.add(i);

			}
			app.selectedMenu=menu;


		} catch (SQLException e) {
			sqlCode = e.getErrorCode(); // Get SQLCODE
			sqlState = e.getSQLState(); // Get SQLSTATE
			System.out.println("Code: " + sqlCode + "  sqlState: " + sqlState);
		}

		addItemsToOrder(statement, myObj,app, brewery);

	}

	private static void addItemsToOrder(Statement statement, Scanner myObj, BreweryDeliveryApplication app, Brewery chosenBrewery) {
		int itemSelected=-1;
		Order tempOrder=new Order(chosenBrewery);
		DecimalFormat df2 = new DecimalFormat("0.00");

		while(itemSelected!=0){
			System.out.println("\nEnter an item number from the menu above to add to add it your order");
			System.out.println("Alternatively, enter [0] to proceed to main menu or [s] to submit your cart");
			//TODO deal with the symbol issue! (try a try-catch on parseInt???)
			//TODO: Add option to delete an item from cart
			String optionChosen="";
			try {
				optionChosen = myObj.nextLine();
			}catch(InputMismatchException e){
				System.out.println("\nOops! You did not input a valid option- try again!\n");
			}
			if(optionChosen.equals("s")){

				if(tempOrder!=null && tempOrder.itemTotal>tempOrder.orderBrewery.order_minimum){
					app.currentOrder=tempOrder;
					finalizeOrder(statement, myObj, app);
					return;
				}else{
					System.out.println("You have not added enough items to your cart! The order minimum is $"+
							tempOrder.orderBrewery.order_minimum+"\n");
				}
			}else if (parseInt(optionChosen)==0){
				return;
			}else if(parseInt(optionChosen)>0 && parseInt(optionChosen)<=app.selectedMenu.size()){
				MenuItem selectedItem=app.selectedMenu.get(parseInt(optionChosen)-1);
				System.out.println("Item Selected: "+selectedItem.name);
				System.out.println("Price: "+selectedItem.price);
				System.out.println("Description: "+selectedItem.description);

				System.out.println("\nPlease indicate any special instructions for the restaurant (ex: no lemon) or press [Enter] to continue");
				String specialInstructions=myObj.nextLine();
				int quantity=-1;
				while(quantity<=0){
					System.out.println("\nPlease indicate the quantity you would like:");
					try {
						quantity = myObj.nextInt();
					}catch(InputMismatchException e){
						System.out.println("Error: Please try again with an integer value greater than 1!");
					}
					myObj.nextLine();
					if(quantity<=0){
						System.out.println("Error: Please try again with an integer value greater than 1!");
					}
				}

				//take quantity into account here
				tempOrder.addItem(new OrderItem(selectedItem.name,selectedItem.price,selectedItem.description, specialInstructions,quantity));
				System.out.println("\nItem Added!\nCart Summary:");
				for(OrderItem i: tempOrder.items){
					System.out.println("x"+i.itemQuantity+" "+i.name+" ....... $"+df2.format(i.price));
				}
				System.out.println("Item Total ....... $"+df2.format(tempOrder.itemTotal));

			}else{
				System.out.println("\nOops! You did not input a valid option- try again!\n");
			}

		}
	}
	private static void finalizeOrder(Statement statement, Scanner myObj, BreweryDeliveryApplication app){
		DecimalFormat df2 = new DecimalFormat("0.00");
		int option=-1;
		String deliveryInstructions="";

		//CHOOSE DELIVERY OR PICKUP
		while(option!=1 && option!=2){

			System.out.println("Is your order for delivery or pick up?");
			System.out.println("[1] Delivery");
			System.out.println("[2] Pickup");
			try {
				option = myObj.nextInt();
				if(option!=1 && option!=2){
					System.out.println("Oops! your input was invalid- try again!");
				}
			}catch(InputMismatchException e){
				System.out.println("Oops! your input was invalid- try again!");
			}
			myObj.nextLine();
		}

		//ADD TIP AND DELIVERY DESCRIPTION IF ORDER IS FOR DELIVERY
		if(option==1){
			app.currentOrder.setDeliveryFee(3.00); //default!
			boolean validTip=false;
			double tipInput=0.0;

			while(!validTip){
				System.out.println("Enter an amount to tip the driver:");
				try{
					tipInput=myObj.nextDouble();
					validTip=true;
				}catch(InputMismatchException e){
					System.out.println("Oops, please input a decimal number as your driver tip!");
				}
				myObj.nextLine();
			}

			app.currentOrder.setTip(tipInput);

			System.out.println("Enter any instructions you might have for the driver or just press [Enter] to leave it blank");
			deliveryInstructions=myObj.nextLine();
		}
		app.currentOrder.calculateTotal();

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();

		//INSERT ORDER INTO DATABASE
		try{
			//ASSIGN ORDER NUMBER: just goes chronologically (not ideal but works to make it unique!)
			String getMaxOrderNumSQL= "SELECT MAX(order_num) as order_num FROM cust_order";
			java.sql.ResultSet rs = statement.executeQuery ( getMaxOrderNumSQL) ;
			int orderNum=0;
			while(rs.next()){
				orderNum=rs.getInt("order_num")+1;
			}

			//INSERT INTO ORDER TABLE
			String insertOrder= "INSERT INTO cust_order VALUES("+orderNum+", \'received\',"+app.currentOrder.tip+", \'"+
					app.user.email+"\', \'"+app.currentOrder.orderBrewery.name+"\', \'"+app.currentOrder.orderBrewery.address+
					"\', \'"+dtf.format(now)+"\');";
			statement.executeUpdate(insertOrder);


			//INSERT ITEMS INTO CONSISTS_OF TABLE
			String insertConsistsOf="INSERT INTO consists_of VALUES";
			int orderCounter=0;
			for (OrderItem i: app.currentOrder.items){
				orderCounter++;
				insertConsistsOf+="("+orderNum+",\'"+i.name+"\', \'"+app.currentOrder.orderBrewery.name+"\', \'"
						+app.currentOrder.orderBrewery.address+"\', \'"+i.specialInstructions+"\',"+i.itemQuantity+")";
				if(orderCounter<app.currentOrder.items.size()){
					insertConsistsOf+=",";
				}

			}
			statement.executeUpdate(insertConsistsOf);


			if(option==1){
				//ASSIGN RANDOM DRIVER TO DELIVERY
				String getDrivers="SELECT email FROM driver";
				ArrayList<String> drivers= new ArrayList<>();
				java.sql.ResultSet driverQueryResult = statement.executeQuery ( getDrivers) ;

				while(driverQueryResult.next()){
					drivers.add(driverQueryResult.getString("email"));
				}

				String randomDriverEmail= drivers.get((int) Math.random()*(drivers.size()-1));

				String insertDelivery= "INSERT INTO delivery VALUES("+orderNum+", \'"+deliveryInstructions+"\', "+
						app.currentOrder.deliveryFee+", \'"+randomDriverEmail+"\', NULL);";
				statement.executeUpdate(insertDelivery);
			}else{
				String insertPickup= "INSERT INTO pickup VALUES("+orderNum+", \'"+app.user.email +"\');";
				statement.executeUpdate(insertPickup);
			}
			//PRINT OUT RECEIPT
			printReceipt(app.currentOrder);

		}catch(SQLException e ){
			System.out.println("Oops- there was an internal error, please try again!");
		}


	}

	private static void displayRestaurantsByRating(Statement statement, Scanner myObj, BreweryDeliveryApplication app) {
		int sqlCode=0;      // Variable to hold SQLCODE
		String sqlState="00000";  // Variable to hold SQLSTATE
		int ratingValue=0;

		while(!(ratingValue<=5 && ratingValue>0)){
			System.out.println("\n**VIEW BREWERIES BY RATING **\n");
			System.out.println("Select the rating values you want to see (5= perfect rating):");
			System.out.println("[1] Rating >= 1");
			System.out.println("[2] Rating >= 2");
			System.out.println("[3] Rating >= 3");
			System.out.println("[4] Rating >= 4");
			System.out.println("[5] Rating =5");

			try{
				ratingValue=myObj.nextInt();
				if(!(ratingValue<=5 && ratingValue>0)){
					System.out.println("Oops! You did not select a valid option!");
				}
			}catch(InputMismatchException e){
				System.out.println("Oops! You did not enter a valid value!");
			}
			myObj.nextLine();


		}


		try {

			String loginSQL = "SELECT * FROM brewery WHERE brewery_rating >="+ratingValue;
			//System.out.println(loginSQL);
			java.sql.ResultSet rs = statement.executeQuery ( loginSQL ) ;
			ArrayList<Brewery> breweries=new ArrayList<>();
			int breweryCount= 0;
			while ( rs.next ( ) ) {
				breweryCount++;
				String breweryName = rs.getString("brewery_name");
				String address = rs.getString("street_address");
				double orderMin = rs.getDouble("order_minimum");
				String priceLevel= rs.getString("price_level");
				double breweryRating= rs.getDouble("brewery_rating");
				String openingTime= rs.getString("opening_time");
				String closingTime= rs.getString("closing_time");

				System.out.println("["+breweryCount+"] "+breweryName);
				System.out.println("	Address: "+address);
				System.out.println("	PriceLevel: "+priceLevel);
				System.out.println("	Order Minimum: "+orderMin);
				System.out.println("	Rating: "+breweryRating);
				System.out.println("	Opening hours: "+openingTime+" - "+closingTime+"\n\n");

				Brewery b= new Brewery(breweryName, address, orderMin);
				breweries.add(b);

			}
			app.breweries=breweries;


		} catch (SQLException e) {
			sqlCode = e.getErrorCode(); // Get SQLCODE
			sqlState = e.getSQLState(); // Get SQLSTATE
			System.out.println("Code: " + sqlCode + "  sqlState: " + sqlState);
		}
		selectBrewery(statement, myObj,app);
	}

	private static void displayAllRestaurants(Statement statement, Scanner myObj, BreweryDeliveryApplication app) {
		int sqlCode=0;      // Variable to hold SQLCODE
		String sqlState="00000";  // Variable to hold SQLSTATE
		System.out.println("\n**ALL BREWERIES**\n");
		try {
			String loginSQL = "SELECT * FROM brewery ";
			//System.out.println(loginSQL);
			java.sql.ResultSet rs = statement.executeQuery ( loginSQL ) ;
			ArrayList<Brewery> breweries=new ArrayList<>();
			int breweryCount= 0;
			while ( rs.next ( ) ) {
				breweryCount++;
				String breweryName = rs.getString("brewery_name");
				String address = rs.getString("street_address");
				double orderMin = rs.getDouble("order_minimum");
				String priceLevel= rs.getString("price_level");
				double breweryRating= rs.getDouble("brewery_rating");
				String openingTime= rs.getString("opening_time");
				String closingTime= rs.getString("closing_time");

				System.out.println("["+breweryCount+"] "+breweryName);
				System.out.println("	Address: "+address);
				System.out.println("	PriceLevel: "+priceLevel);
				System.out.println("	Order Minimum: "+orderMin);
				System.out.println("	Rating: "+breweryRating);
				System.out.println("	Opening hours: "+openingTime+" - "+closingTime+"\n\n");

				Brewery b= new Brewery(breweryName, address, orderMin);
				breweries.add(b);

			}
			app.breweries=breweries;


		} catch (SQLException e) {
			sqlCode = e.getErrorCode(); // Get SQLCODE
			sqlState = e.getSQLState(); // Get SQLSTATE
			System.out.println("Code: " + sqlCode + "  sqlState: " + sqlState);
		}
		selectBrewery(statement, myObj,app);
	}

	private static boolean registerUser(Statement statement, Scanner myObj, BreweryDeliveryApplication app) {
		String userEmail, password, firstName, lastName, phoneNum, birthDate;
		int sqlCode=0;      // Variable to hold SQLCODE
		String sqlState="00000";  // Variable to hold SQLSTATE

		System.out.println("First Name:");
		firstName= myObj.nextLine();

		System.out.println("Last Name:");
		lastName= myObj.nextLine();

		System.out.println("Enter Email:");
		userEmail = myObj.nextLine();  // Read user input

		System.out.println("Enter Password:");
		password= myObj.nextLine();

		System.out.println("Birthdate (YYYY-MM-DD)");
		birthDate= myObj.nextLine();

		System.out.println("Phone Number:");
		phoneNum= myObj.nextLine();


		try {
			//System.out.println ( insertSQL ) ;

			//TODO: should loop through this part of the menu until people fill it out right; maybe if they can't, delete them from database
			System.out.println("CUSTOMER DETAILS");
			System.out.println("Please fill in a few more details to create your account!\n");

			System.out.println("DELIVERY ADDRESS");
			System.out.println("Street address:");
			String deliveryStreetAddress=myObj.nextLine();
			System.out.println("Postal Code:");
			String deliveryPostalCode=myObj.nextLine();

			System.out.println("BILLING ADDRESS");
			System.out.println("Street address:");
			String billingStreetAddress=myObj.nextLine();
			System.out.println("Postal Code:");
			String billingPostalCode=myObj.nextLine();

			System.out.println("Credit Card Number:");
			String creditCardNum=myObj.nextLine();

			String insertSQL = "INSERT INTO app_user VALUES (\'"+userEmail+"\' , \'"+firstName+"\' , \'"+lastName+
					"\' , \'"+password+"\' , \'"+birthDate+"\' , \'"+phoneNum+"\');"
					+ " INSERT INTO address VALUES (\'"+deliveryStreetAddress+"\', \'"+deliveryPostalCode+"\'), (\'"+
					billingStreetAddress+"\', \'"+billingPostalCode+"\') ON CONFLICT DO NOTHING;"
					+" INSERT INTO customer VALUES (\'"+userEmail+"\' , \'"+deliveryStreetAddress+"\' , \'"+billingStreetAddress+
						"\' , \'"+creditCardNum+"\')";

			statement.executeUpdate ( insertSQL ) ;

			app.user=new User(userEmail, password);
			return true;

		} catch (SQLException e) {
			sqlCode = e.getErrorCode(); // Get SQLCODE
			sqlState = e.getSQLState(); // Get SQLSTATE
			// Your code to handle errors comes here;
			// something more meaningful than a print would be good
			System.out.println("Code: " + sqlCode + "  sqlState: " + sqlState);
			return false;
		}


	}

	private static boolean loginUser(Statement statement, Scanner myObj, BreweryDeliveryApplication app) {

		int sqlCode=0;      // Variable to hold SQLCODE
		String sqlState="00000";


		System.out.println("Enter Email:");
		String userEmail = myObj.nextLine();  // Read user input


		System.out.println("Enter Password:");
		String password= myObj.nextLine();


		try {
			String loginSQL = "SELECT pwd FROM app_user WHERE email=\'"+userEmail+"\'";
			//System.out.println(loginSQL);
			java.sql.ResultSet rs = statement.executeQuery ( loginSQL ) ;
			while ( rs.next ( ) ) {
				String registeredPwd = rs.getString("pwd");

				if(!registeredPwd.equals(password)){
					System.out.println(registeredPwd);
					System.out.println("Wrong Password! Try again");
					return false;
				}

			}
			String verifyCustomerSQL = "SELECT email FROM customer WHERE email=\'"+userEmail+"\'";
			java.sql.ResultSet rs2 = statement.executeQuery ( verifyCustomerSQL ) ;
			while ( rs2.next ( ) ) {
				//String email=rs2.getString("email");
				app.user= new User(userEmail, password);
				return true;
			}

			return false;


		} catch (SQLException e) {
			sqlCode = e.getErrorCode(); // Get SQLCODE
			sqlState = e.getSQLState(); // Get SQLSTATE
			System.out.println("Code: " + sqlCode + "  sqlState: " + sqlState);
			return false;
		}


	}

	public static int initiationScreen(Scanner myObj){
		System.out.println("**** WELCOME TO BREW-TO-ME ****");
		System.out.println("oooooo\n" +
				"i====i_\n" +
				"|    |_)\n" +
				"|    |   \n" +
				"`-==-'");
		System.out.println(" To select an option, input the number in square brackets next " +
				"to the option you wish to select");

		int loginOption=0;
		while(loginOption != 1 && loginOption !=2){
			System.out.println("[1] Login");
			System.out.println("[2] Register");

			try {
				loginOption = myObj.nextInt();
				if(loginOption != 1 && loginOption !=2){
					System.out.println("Oops! You did not select 1 or 2, try again!");
				}
			}catch(InputMismatchException e){
				System.out.println("Oops! You did not select 1 or 2, try again!");
			}
			myObj.nextLine();


		}
		return loginOption;
	}

	public static int mainMenuScreen(Scanner myObj) {
		int selectedOption=-1;
		while(selectedOption!=0 && selectedOption !=1 && selectedOption !=2 && selectedOption!=3 && selectedOption!=4 && selectedOption!=5){
			System.out.println("[0] Logout");
			System.out.println("[1] Browse All Breweries");
			System.out.println("[2] Browse Breweries by Rating");
			System.out.println("[3] Browse Breweries by Price Level");
			System.out.println("[4] View Order History");
			System.out.println("[5] Change Delivery Address");


			try{
				selectedOption=myObj.nextInt();

				if(selectedOption !=0 && selectedOption !=1 && selectedOption !=2 && selectedOption !=3 && selectedOption!=4 && selectedOption!=5){
					System.out.println("Oops! You did not select a valid menu option- try again!");
				}
			}catch(InputMismatchException e){
				System.out.println("Oops! You did not select a valid menu option- try again!");
			}
			myObj.nextLine();


		}

		return selectedOption;
	}
	public static boolean emailVerification(){

		//TODO : verify that emails are in the right format and not blank etc....
		return true;
	}
	public static boolean password(){
		//TODO: verify that passwords are in an okay format

		return true;
	}


}

