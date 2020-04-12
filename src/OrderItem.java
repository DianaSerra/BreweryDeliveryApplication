public class OrderItem extends MenuItem {
        String specialInstructions;
        int itemQuantity;

        OrderItem(String name, double price, String description, String specialInstructions, int quantity){
            super(name,price, description);
            this.specialInstructions=specialInstructions;
            this.itemQuantity=quantity;
        }

}
