public class Brewery {

    String name;
    String address;
    double order_minimum;


    Brewery(String name, String address){
        this.name=name;
        this.address=address;
    }
    Brewery(String name, String address, double order_minimum){
        this.name=name;
        this.address=address;
        this.order_minimum=order_minimum;
    }

}
