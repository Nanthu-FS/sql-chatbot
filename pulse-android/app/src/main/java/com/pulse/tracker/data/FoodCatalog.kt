package com.pulse.tracker.data

/**
 * A small built-in catalogue of common foods so search works offline.
 * Values are typical per-100 g figures (generic, unbranded); barcode
 * scans and custom foods cover everything else.
 */
object FoodCatalog {

    private fun f(
        id: String, name: String, kcal: Double, p: Double, c: Double, fat: Double,
        serving: String = "100 g", grams: Double = 100.0,
    ) = Food(id, name, kcal, p, c, fat, serving, grams)

    val foods: List<Food> = listOf(
        // Protein
        f("chicken_breast", "Grilled chicken breast", 165.0, 31.0, 0.0, 3.6, "1 breast", 150.0),
        f("chicken_thigh", "Chicken thigh, roasted", 209.0, 26.0, 0.0, 10.9, "1 thigh", 110.0),
        f("turkey_mince", "Turkey mince, cooked", 203.0, 27.0, 0.0, 10.0),
        f("beef_mince", "Lean beef mince, cooked", 217.0, 26.0, 0.0, 12.0),
        f("steak", "Sirloin steak, grilled", 206.0, 29.0, 0.0, 9.0, "1 steak", 200.0),
        f("salmon", "Salmon fillet, baked", 208.0, 22.0, 0.0, 13.0, "1 fillet", 140.0),
        f("tuna_can", "Tuna in water, drained", 116.0, 26.0, 0.0, 1.0, "1 can", 112.0),
        f("shrimp", "Shrimp, cooked", 99.0, 24.0, 0.2, 0.3),
        f("egg", "Egg, whole", 143.0, 12.6, 0.7, 9.5, "1 large", 50.0),
        f("egg_white", "Egg whites", 52.0, 11.0, 0.7, 0.2, "3 whites", 99.0),
        f("tofu", "Tofu, firm", 144.0, 17.0, 3.0, 9.0),
        f("paneer", "Paneer", 296.0, 20.0, 3.5, 22.0),
        f("tempeh", "Tempeh", 193.0, 20.0, 9.0, 11.0),
        f("whey", "Whey protein shake", 400.0, 80.0, 8.0, 6.0, "1 scoop", 30.0),
        f("lentils", "Lentils, cooked", 116.0, 9.0, 20.0, 0.4, "1 cup", 198.0),
        f("chickpeas", "Chickpeas, cooked", 164.0, 8.9, 27.0, 2.6, "1 cup", 164.0),
        f("black_beans", "Black beans, cooked", 132.0, 8.9, 24.0, 0.5, "1 cup", 172.0),
        f("dal", "Dal (lentil curry)", 120.0, 6.5, 16.0, 3.5, "1 bowl", 250.0),
        // Dairy
        f("greek_yogurt", "Greek yogurt, plain 2%", 73.0, 10.0, 3.9, 2.0, "1 cup", 200.0),
        f("yogurt_berries", "Greek yogurt & berries", 93.0, 8.0, 11.0, 1.8, "1 bowl", 300.0),
        f("milk", "Milk, semi-skimmed", 47.0, 3.4, 4.8, 1.7, "1 glass", 250.0),
        f("oat_milk", "Oat milk", 46.0, 1.0, 6.7, 1.5, "1 glass", 250.0),
        f("cottage_cheese", "Cottage cheese", 98.0, 11.0, 3.4, 4.3, "1/2 cup", 113.0),
        f("cheddar", "Cheddar cheese", 403.0, 25.0, 1.3, 33.0, "1 slice", 28.0),
        f("mozzarella", "Mozzarella", 280.0, 28.0, 3.1, 17.0),
        f("butter", "Butter", 717.0, 0.9, 0.1, 81.0, "1 tbsp", 14.0),
        // Grains
        f("white_rice", "White rice, cooked", 130.0, 2.7, 28.0, 0.3, "1 cup", 158.0),
        f("brown_rice", "Brown rice, cooked", 123.0, 2.7, 26.0, 1.0, "1 cup", 195.0),
        f("basmati", "Basmati rice, cooked", 121.0, 3.5, 25.0, 0.4, "1 cup", 160.0),
        f("quinoa", "Quinoa, cooked", 120.0, 4.4, 21.0, 1.9, "1 cup", 185.0),
        f("oats", "Rolled oats, dry", 379.0, 13.0, 68.0, 6.5, "1/2 cup", 40.0),
        f("porridge", "Porridge with milk", 95.0, 4.0, 13.0, 3.0, "1 bowl", 250.0),
        f("pasta", "Pasta, cooked", 158.0, 5.8, 31.0, 0.9, "1 cup", 140.0),
        f("bread_white", "White bread", 265.0, 9.0, 49.0, 3.2, "1 slice", 30.0),
        f("bread_wholemeal", "Wholemeal bread", 247.0, 13.0, 41.0, 3.4, "1 slice", 32.0),
        f("sourdough", "Sourdough bread", 266.0, 11.0, 51.0, 1.9, "1 slice", 50.0),
        f("bagel", "Bagel, plain", 257.0, 10.0, 50.0, 1.7, "1 bagel", 105.0),
        f("tortilla", "Flour tortilla", 304.0, 8.0, 50.0, 8.0, "1 wrap", 62.0),
        f("roti", "Roti / chapati", 297.0, 9.8, 46.0, 7.5, "1 roti", 40.0),
        f("naan", "Naan bread", 310.0, 9.0, 50.0, 8.0, "1 naan", 90.0),
        f("granola", "Granola", 471.0, 10.0, 64.0, 20.0, "1/2 cup", 60.0),
        f("cornflakes", "Cornflakes", 357.0, 7.5, 84.0, 0.4, "1 bowl", 30.0),
        f("potato", "Potato, boiled", 87.0, 1.9, 20.0, 0.1, "1 medium", 170.0),
        f("sweet_potato", "Sweet potato, baked", 90.0, 2.0, 21.0, 0.2, "1 medium", 150.0),
        f("fries", "French fries", 312.0, 3.4, 41.0, 15.0, "1 medium", 117.0),
        f("idli", "Idli", 146.0, 4.5, 30.0, 0.6, "2 idli", 80.0),
        f("dosa", "Plain dosa", 168.0, 3.9, 29.0, 3.7, "1 dosa", 100.0),
        f("upma", "Upma", 150.0, 3.5, 22.0, 5.0, "1 bowl", 200.0),
        f("poha", "Poha", 130.0, 2.6, 23.0, 3.0, "1 bowl", 200.0),
        // Fruit
        f("banana", "Banana", 89.0, 1.1, 23.0, 0.3, "1 medium", 118.0),
        f("apple", "Apple", 52.0, 0.3, 14.0, 0.2, "1 medium", 182.0),
        f("orange", "Orange", 47.0, 0.9, 12.0, 0.1, "1 medium", 131.0),
        f("blueberries", "Blueberries", 57.0, 0.7, 14.0, 0.3, "1 cup", 148.0),
        f("strawberries", "Strawberries", 32.0, 0.7, 7.7, 0.3, "1 cup", 152.0),
        f("grapes", "Grapes", 69.0, 0.7, 18.0, 0.2, "1 cup", 151.0),
        f("mango", "Mango", 60.0, 0.8, 15.0, 0.4, "1 cup", 165.0),
        f("pineapple", "Pineapple", 50.0, 0.5, 13.0, 0.1, "1 cup", 165.0),
        f("watermelon", "Watermelon", 30.0, 0.6, 7.6, 0.2, "1 wedge", 286.0),
        f("avocado", "Avocado", 160.0, 2.0, 8.5, 15.0, "1/2 fruit", 100.0),
        f("dates", "Dates, medjool", 277.0, 1.8, 75.0, 0.2, "2 dates", 48.0),
        // Veg
        f("broccoli", "Broccoli, steamed", 35.0, 2.4, 7.2, 0.4, "1 cup", 156.0),
        f("spinach", "Spinach, raw", 23.0, 2.9, 3.6, 0.4, "1 cup", 30.0),
        f("salad", "Mixed green salad", 20.0, 1.5, 3.5, 0.2, "1 bowl", 150.0),
        f("carrot", "Carrot", 41.0, 0.9, 10.0, 0.2, "1 medium", 61.0),
        f("tomato", "Tomato", 18.0, 0.9, 3.9, 0.2, "1 medium", 123.0),
        f("cucumber", "Cucumber", 15.0, 0.7, 3.6, 0.1, "1/2 cucumber", 150.0),
        f("peas", "Green peas", 81.0, 5.4, 14.0, 0.4, "1/2 cup", 80.0),
        f("corn", "Sweet corn", 86.0, 3.3, 19.0, 1.4, "1 cob", 90.0),
        f("mushrooms", "Mushrooms, sautéed", 54.0, 3.6, 4.4, 3.3, "1 cup", 108.0),
        // Fats & snacks
        f("almonds", "Almonds", 579.0, 21.0, 22.0, 50.0, "1 handful", 28.0),
        f("peanut_butter", "Peanut butter", 588.0, 25.0, 20.0, 50.0, "1 tbsp", 16.0),
        f("walnuts", "Walnuts", 654.0, 15.0, 14.0, 65.0, "1 handful", 28.0),
        f("cashews", "Cashews", 553.0, 18.0, 30.0, 44.0, "1 handful", 28.0),
        f("olive_oil", "Olive oil", 884.0, 0.0, 0.0, 100.0, "1 tbsp", 13.5),
        f("dark_chocolate", "Dark chocolate 70%", 598.0, 7.8, 46.0, 43.0, "2 squares", 20.0),
        f("protein_bar", "Protein bar", 380.0, 33.0, 38.0, 12.0, "1 bar", 60.0),
        f("crisps", "Potato crisps", 536.0, 7.0, 53.0, 35.0, "1 bag", 30.0),
        f("popcorn", "Popcorn, air-popped", 387.0, 13.0, 78.0, 4.5, "1 bowl", 25.0),
        f("hummus", "Hummus", 166.0, 7.9, 14.0, 9.6, "2 tbsp", 30.0),
        f("cookie", "Chocolate chip cookie", 488.0, 5.4, 64.0, 24.0, "1 cookie", 30.0),
        f("ice_cream", "Vanilla ice cream", 207.0, 3.5, 24.0, 11.0, "1 scoop", 66.0),
        // Meals
        f("chicken_rice_bowl", "Chicken rice bowl", 150.0, 11.0, 18.0, 3.5, "1 bowl", 360.0),
        f("burrito", "Chicken burrito", 210.0, 11.0, 24.0, 7.5, "1 burrito", 300.0),
        f("pizza", "Margherita pizza", 266.0, 11.0, 33.0, 10.0, "1 slice", 107.0),
        f("burger", "Cheeseburger", 263.0, 15.0, 22.0, 13.0, "1 burger", 220.0),
        f("sushi", "Salmon sushi roll", 150.0, 6.0, 26.0, 2.5, "6 pieces", 180.0),
        f("caesar_salad", "Chicken caesar salad", 127.0, 9.0, 5.0, 8.0, "1 bowl", 300.0),
        f("biryani", "Chicken biryani", 165.0, 8.0, 21.0, 5.5, "1 plate", 350.0),
        f("butter_chicken", "Butter chicken", 150.0, 12.0, 5.0, 9.0, "1 bowl", 250.0),
        f("fried_rice", "Egg fried rice", 163.0, 4.5, 27.0, 4.0, "1 plate", 300.0),
        f("ramen", "Ramen", 90.0, 4.0, 12.0, 3.0, "1 bowl", 550.0),
        f("sandwich", "Turkey sandwich", 220.0, 14.0, 24.0, 7.0, "1 sandwich", 200.0),
        f("omelette", "Two-egg omelette", 154.0, 11.0, 1.0, 12.0, "1 omelette", 120.0),
        f("pancakes", "Pancakes", 227.0, 6.4, 28.0, 9.7, "2 pancakes", 150.0),
        f("smoothie", "Fruit smoothie", 60.0, 1.5, 13.0, 0.5, "1 glass", 350.0),
        // Drinks
        f("latte", "Latte", 54.0, 3.4, 5.3, 2.2, "1 grande", 350.0),
        f("oat_latte", "Oat milk latte", 40.0, 0.8, 6.0, 1.4, "1 cup", 350.0),
        f("black_coffee", "Black coffee", 1.0, 0.1, 0.0, 0.0, "1 mug", 250.0),
        f("tea_milk", "Tea with milk", 15.0, 0.6, 1.8, 0.6, "1 mug", 250.0),
        f("orange_juice", "Orange juice", 45.0, 0.7, 10.0, 0.2, "1 glass", 250.0),
        f("cola", "Cola", 42.0, 0.0, 10.6, 0.0, "1 can", 330.0),
        f("beer", "Beer", 43.0, 0.5, 3.6, 0.0, "1 pint", 568.0),
        f("wine", "Red wine", 85.0, 0.1, 2.6, 0.0, "1 glass", 175.0),
    )

    private val byId = foods.associateBy { it.id }

    fun find(id: String): Food? = byId[id]

    fun search(query: String, extra: List<Food> = emptyList()): List<Food> {
        val q = query.trim().lowercase()
        val all = extra + foods
        if (q.isEmpty()) return all
        val words = q.split(' ').filter { it.isNotBlank() }
        return all
            .filter { food -> words.all { food.name.lowercase().contains(it) || (food.brand?.lowercase()?.contains(it) == true) } }
            .sortedBy { if (it.name.lowercase().startsWith(q)) 0 else 1 }
    }
}
