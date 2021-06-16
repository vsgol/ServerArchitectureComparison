import sys
from matplotlib import pyplot as plt

if __name__ == '__main__':
    data = {}
    plt.figure(figsize=(6.8, 4.2))
    for filename in ["blocking", "asynchronous"]:
        with open('results/' + filename + '/' + sys.argv[1], 'r') as file:
            parameters = []
            file.readline()
            parameters.append(file.readline())
            line = file.readline()
            _, _, parameter = line.partition(': ')
            file.readline()
            for _ in range(2):
                parameters.append(file.readline())

            file.readline()
            data[filename] = [[], []]
            for line in file:
                val, time = line.rsplit()
                data[filename][0].append(int(val))
                data[filename][1].append(int(time))
            plt.plot(data[filename][0], data[filename][1], label=filename)

    plt.legend()
    plt.xlabel(parameter)
    plt.ylabel('Average time')
    title = ''.join(parameters)
    plt.title(title)
    plt.show()
