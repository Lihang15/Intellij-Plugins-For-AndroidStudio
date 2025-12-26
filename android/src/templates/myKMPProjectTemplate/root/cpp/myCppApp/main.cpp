#include <iostream>
#include <vector>

int add(int a, int b) {
    int c = a + b;
    return c;
}

int main() {
    std::vector<int> nums = {1, 2, 3};
    int result = add(3, 4);
    std::cout << "result = " << result << std::endl;
    return 0;
}
