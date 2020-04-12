import java.util.ArrayList;

public class Order {
    ArrayList<OrderItem> items;
    Brewery orderBrewery;
    double itemTotal;
    double deliveryFee;
    double tip;
    double orderTotal;
    double taxes;
    int orderNum;
    String orderTime;

    Order(Brewery orderBrewery){
        this.orderBrewery=orderBrewery;
        this.items=new ArrayList<OrderItem>();
        this.itemTotal=0;
        this.deliveryFee=0;
        this.orderTotal=0;
        this.tip=0;
        this.taxes=0;
    }
    public void addItem (OrderItem newItem){
        this.itemTotal+=newItem.price*newItem.itemQuantity;

        for(OrderItem i: this.items){
            if(i.name.equals(newItem.name)){
                i.itemQuantity+=newItem.itemQuantity;
                return;
            }
        }
        this.items.add(newItem);
    }
    public void setDeliveryFee(double val){

        //ideally this would be calculated with delivery distance but we'll make it a flat rate for simplicity!
        this.deliveryFee=val;
    }
    public void setTip(double tip){
        this.tip=tip;
    }
    public double calculateTotal(){
        this.taxes=itemTotal*0.15; //roughly GST+PST for montreal!
        this.orderTotal=this.itemTotal+this.deliveryFee+this.tip+this.taxes;
        return this.orderTotal;
    }
    public int setOrderNum(int oNum){
        this.orderNum=oNum;
        return this.orderNum;
    }
    public String setOrderTime(String oTime){
        this.orderTime=oTime;
        return this.orderTime;
    }

}

